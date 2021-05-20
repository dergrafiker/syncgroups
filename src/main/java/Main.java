import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class Main {
    public static void main(String[] args) throws IOException {
        String catchAll = args[0];
        String groupSuffix = args[1];
        String needsToHaveOneInEachGroupKey = args[2];

        Map<String, Set<String>> localMapping = ReadResources.readMemberMapFromExternalFile("mapping");
        Map<String, Set<String>> fromRemote = ReadResources.getMemberMapFromRemoteCSV("groups.csv");

        List<String> onlyRemote = new ArrayList<>();
        List<String> onlyLocal = new ArrayList<>();
        List<String> modifyCommands = new ArrayList<>();
        Multimap<String, String> user2group = HashMultimap.create();

        for (String group : combine(localMapping.keySet(), fromRemote.keySet())) {
            if (group.equalsIgnoreCase(catchAll)) {
                System.out.println("skipping catchall group: " + catchAll);
                continue;
            }

            Set<String> local = localMapping.get(group);
            Set<String> remote = fromRemote.get(group);

            if (local == null) {
                onlyRemote.add(group);
            } else if (remote == null) {
                onlyLocal.add(group);
            } else {
                showDiff(group, local, remote, groupSuffix, modifyCommands, user2group);
            }
        }

        Set<String> allMailsInLocalMapping = localMapping.values().stream().flatMap(Collection::stream).collect(toSet());
        Set<String> allMailsFromRemote = fromRemote.get(catchAll);

        showDiff(catchAll, allMailsInLocalMapping, allMailsFromRemote, groupSuffix, modifyCommands, user2group);

        Set<String> needsToHaveOneInEachGroup = fromRemote.get(needsToHaveOneInEachGroupKey);
        fromRemote.forEach((key, value) -> {
            if (Sets.intersection(value, needsToHaveOneInEachGroup).isEmpty()) {
                System.out.println(key + " has no intersection with " + needsToHaveOneInEachGroupKey);
            }
        });
        System.out.println();
        System.out.println("groups found only remote: " + StringUtils.join(onlyRemote, ", "));
        System.out.println("groups found only local: " + StringUtils.join(onlyLocal, ", "));
        System.out.println();
        user2group.keySet().forEach(key -> System.out.println(key + " " + user2group.get(key)));
        System.out.println();
        System.out.println("run following commands to match groups to mapping:");
        modifyCommands.forEach(System.out::println);
    }

    @SafeVarargs
    private static Set<String> combine(Set<String>... sets) {
        Set<String> allGroups = new HashSet<>();
        for (Set<String> set : sets) {
            allGroups.addAll(set);
        }
        return allGroups;
    }

    private static void showDiff(String group,
                                 Set<String> local,
                                 Set<String> remote,
                                 String groupSuffix,
                                 List<String> modifyCommands,
                                 Multimap<String, String> user2group) {
        Sets.SetView<String> localNotRemote = Sets.difference(local, remote);
        Sets.SetView<String> remoteNotLocal = Sets.difference(remote, local);

        for (String user : localNotRemote) {
            user2group.get(user).add("+" + group);
            modifyCommands.add("gam update group " + group + groupSuffix + " add " + user);
        }
        for (String user : remoteNotLocal) {
            user2group.get(user).add("-" + group);
            modifyCommands.add("gam update group " + group + groupSuffix + " remove " + user);
        }
    }
}
