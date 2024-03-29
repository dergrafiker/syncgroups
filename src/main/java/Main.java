import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class Main {
    public static void main(String[] args) throws IOException {
        String catchAll = args[0]; //group into which every address will be put
        String groupSuffix = args[1]; //suffix to add after group name e.g. '@example.com'
        String needsToHaveOneInEachGroupKey = args[2]; //group that every other group should have one or more members of

        Map<String, Set<String>> localGroupToUserMap = ReadResources.readIntendedGroupToUserMapFromExternalFile("mapping");
        Map<String, Set<String>> remoteGroupToUserMap = ReadResources.readCurrentGroupToUserMapFromRemoteCSV("groups.csv");
        Set<String> allRemoteGroups = ReadResources.readAllRemoteGroups("remoteGroups");

        List<String> groupsFoundOnlyOnRemoteEnd = new ArrayList<>();
        List<String> groupsFoundOnlyOnLocalEnd = new ArrayList<>();
        List<String> addCommands = new ArrayList<>();
        List<String> removeCommands = new ArrayList<>();
        Multimap<String, String> user2group = HashMultimap.create();

        Set<String> allKnownGroups = combine(localGroupToUserMap.keySet(), allRemoteGroups);
        for (String group : allKnownGroups) {
            if (group.equalsIgnoreCase(catchAll)) {
                System.out.println("skipping catchall group: " + catchAll);
                continue;
            }

            if (!localGroupToUserMap.containsKey(group)) {
                groupsFoundOnlyOnRemoteEnd.add(group);
            } else if (!allRemoteGroups.contains(group)) {
                groupsFoundOnlyOnLocalEnd.add(group);
            } else {
                collectDifferences(group,
                        localGroupToUserMap.getOrDefault(group, Collections.emptySet()),
                        remoteGroupToUserMap.getOrDefault(group, Collections.emptySet()),
                        groupSuffix, addCommands, removeCommands, user2group);
            }
        }

        //collect Differences for catchall group
        Set<String> allMailsInLocalMapping = localGroupToUserMap.values().stream().flatMap(Collection::stream).collect(toSet());
        Set<String> allMailsInCatchallFromRemote = remoteGroupToUserMap.get(catchAll);
        collectDifferences(catchAll, allMailsInLocalMapping, allMailsInCatchallFromRemote, groupSuffix, addCommands, removeCommands, user2group);

        Set<String> usersThatShouldBePresentInOtherGroups = new HashSet<>();
        usersThatShouldBePresentInOtherGroups.addAll(remoteGroupToUserMap.getOrDefault(needsToHaveOneInEachGroupKey, Collections.emptySet()));
        usersThatShouldBePresentInOtherGroups.addAll(localGroupToUserMap.getOrDefault(needsToHaveOneInEachGroupKey, Collections.emptySet()));

        remoteGroupToUserMap.forEach((key, value) -> {
            if (Sets.intersection(value, usersThatShouldBePresentInOtherGroups).isEmpty()) {
                System.out.println(key + " has no intersection with " + needsToHaveOneInEachGroupKey);
            }
        });
        System.out.println();
        System.out.println("groups found only remote: " + StringUtils.join(groupsFoundOnlyOnRemoteEnd, ", "));
        System.out.println("groups found only local: " + StringUtils.join(groupsFoundOnlyOnLocalEnd, ", "));
        System.out.println();

        user2group.keySet().stream().sorted()
                .forEach(key -> System.out.println(key + " " + user2group.get(key)));

        System.out.println();
        System.out.println("run following commands to match groups to mapping:");
        addCommands.forEach(System.out::println);
        System.out.println();
        removeCommands.forEach(System.out::println);
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
