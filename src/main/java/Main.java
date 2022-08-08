import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public class Main {
    public static void main(String[] args) throws IOException {
        String catchAll = args[0];
        String groupSuffix = args[1];
        String needsToHaveOneInEachGroupKey = args[2];

        Map<String, Set<String>> localGroupToUserMap = ReadResources.readIntendedGroupToUserMapFromExternalFile("mapping");

        /*
        //find diff between allUsers and localMapping
        Set<String> uniqueMailAdresses = getUniqueMailAdresses("allmembers");
        Set<String> allUsersFromMapping = localGroupToUserMap.values().stream().flatMap(Collection::stream).collect(toSet());
        Sets.SetView<String> first = Sets.difference(uniqueMailAdresses, allUsersFromMapping);
        Sets.SetView<String> second = Sets.difference(allUsersFromMapping, uniqueMailAdresses);
        System.out.println();
         */

        Map<String, Set<String>> remoteGroupToUserMap = ReadResources.readCurrentGroupToUserMapFromRemoteCSV("groups.csv");

        List<String> groupsFoundOnlyOnRemoteEnd = new ArrayList<>();
        List<String> groupsFoundOnlyOnLocalEnd = new ArrayList<>();
        List<String> addCommands = new ArrayList<>();
        List<String> removeCommands = new ArrayList<>();
        Multimap<String, String> user2group = HashMultimap.create();

        Set<String> allKnownGroups = combine(localGroupToUserMap.keySet(), remoteGroupToUserMap.keySet());
        for (String group : allKnownGroups) {
            if (group.equalsIgnoreCase(catchAll)) {
                System.out.println("skipping catchall group: " + catchAll);
                continue;
            }

            if (!localGroupToUserMap.containsKey(group)) {
                groupsFoundOnlyOnRemoteEnd.add(group);
            } else if (!remoteGroupToUserMap.containsKey(group)) {
                groupsFoundOnlyOnLocalEnd.add(group);
            } else {
                collectDifferences(group, localGroupToUserMap.get(group), remoteGroupToUserMap.get(group),
                        groupSuffix, addCommands, removeCommands, user2group);
            }
        }

        //collect Differences for catchall group
        Set<String> allMailsInLocalMapping = localGroupToUserMap.values().stream().flatMap(Collection::stream).collect(toSet());
        Set<String> allMailsFromRemote = remoteGroupToUserMap.get(catchAll);
        collectDifferences(catchAll, allMailsInLocalMapping, allMailsFromRemote, groupSuffix, addCommands, removeCommands, user2group);

        Set<String> usersThatShouldBePresentInOtherGroups = new HashSet<>();
        usersThatShouldBePresentInOtherGroups.addAll(remoteGroupToUserMap.get(needsToHaveOneInEachGroupKey));
        usersThatShouldBePresentInOtherGroups.addAll(localGroupToUserMap.get(needsToHaveOneInEachGroupKey));

        remoteGroupToUserMap.forEach((key, value) -> {
            if (Sets.intersection(value, usersThatShouldBePresentInOtherGroups).isEmpty()) {
                System.out.println(key + " has no intersection with " + needsToHaveOneInEachGroupKey);
            }
        });
        System.out.println();
        System.out.println("groups found only remote: " + StringUtils.join(groupsFoundOnlyOnRemoteEnd, ", "));
        System.out.println("groups found only local: " + StringUtils.join(groupsFoundOnlyOnLocalEnd, ", "));
        System.out.println();
        user2group.keySet().forEach(key -> System.out.println(key + " " + user2group.get(key)));
        System.out.println();
        System.out.println("run following commands to match groups to mapping:");
        addCommands.forEach(System.out::println);
        System.out.println();
        removeCommands.forEach(System.out::println);
    }

    private static Set<String> getUniqueMailAdresses(String resourceName) throws IOException {
        try {
            URL allmembers = Main.class.getResource(resourceName);
            Objects.requireNonNull(allmembers);
            Path path = Paths.get(allmembers.toURI());
            List<String> allLines = Files.readAllLines(path);

            Splitter splitter = Splitter.onPattern("[;\\s]+")
                    .trimResults()
                    .omitEmptyStrings();

            return allLines.stream()
                    .flatMap(s -> {
                        List<String> strings = splitter.splitToList(s);
                        Logger.debug("{} => {}", s, strings);
                        return strings.stream().map(String::toLowerCase);
                    })
                    .collect(Collectors.toSet());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    private static Set<String> combine(Set<String>... sets) {
        Set<String> allGroups = new HashSet<>();
        for (Set<String> set : sets) {
            allGroups.addAll(set);
        }
        return allGroups;
    }

    private static void collectDifferences(String group,
                                           Set<String> usersFromIntendedMapping,
                                           Set<String> usersFromRemoteMapping,
                                           String groupSuffix,
                                           List<String> addCommands,
                                           List<String> removeCommands,
                                           Multimap<String, String> user2group) {
        collectUsersToAdd(group, usersFromIntendedMapping, usersFromRemoteMapping, groupSuffix, addCommands, user2group);
        collectUsersToRemove(group, usersFromIntendedMapping, usersFromRemoteMapping, groupSuffix, removeCommands, user2group);
    }

    private static void collectUsersToRemove(String groupWhereUserIsMissing, Set<String> usersFromIntendedMapping, Set<String> usersFromRemoteMapping,
                                             String groupSuffix, List<String> removeCommands, Multimap<String, String> user2group) {
        Sets.SetView<String> remoteNotLocal = Sets.difference(usersFromRemoteMapping, usersFromIntendedMapping);
        for (String user : remoteNotLocal) {
            user2group.get(user).add("-" + groupWhereUserIsMissing);
            removeCommands.add("gam update group " + groupWhereUserIsMissing + groupSuffix + " remove " + user);
        }
    }

    private static void collectUsersToAdd(String group, Set<String> usersFromIntendedMapping, Set<String> usersFromRemoteMapping,
                                          String groupSuffix, List<String> addCommands, Multimap<String, String> user2group) {
        Sets.SetView<String> localNotRemote = Sets.difference(usersFromIntendedMapping, usersFromRemoteMapping);
        for (String user : localNotRemote) {
            user2group.get(user).add("+" + group);
            addCommands.add("gam update group " + group + groupSuffix + " add " + user);
        }
    }
}
