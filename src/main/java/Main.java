import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class Main {
    public static void main(String[] args) throws IOException {
        String catchAll = args[0];

        Map<String, Set<String>> localMapping = ReadResources.readMemberMapFromExternalFile("mapping");
        Map<String, Set<String>> fromRemote = ReadResources.getMemberMapFromRemoteCSV("out.csv");

        for (String group : combine(localMapping.keySet(), fromRemote.keySet())) {
            if (group.equalsIgnoreCase(catchAll)) {
                System.out.println("skipping catchall group: " + catchAll);
                continue;
            }

            Set<String> local = localMapping.get(group);
            Set<String> remote = fromRemote.get(group);

            if (local == null) {
                System.out.println(group + " is found only in remote");
            } else if (remote == null) {
                System.out.println(group + " is found only in local");
            } else {
                showDiff(group, local, remote);
            }
        }

        Set<String> allMailsInLocalMapping = localMapping.values().stream().flatMap(Collection::stream).collect(toSet());
        Set<String> allMailsFromRemote = fromRemote.get(catchAll);

        showDiff(catchAll, allMailsInLocalMapping, allMailsFromRemote);
    }

    @SafeVarargs
    private static Set<String> combine(Set<String>... sets) {
        Set<String> allGroups = new HashSet<>();
        for (Set<String> set : sets) {
            allGroups.addAll(set);
        }
        return allGroups;
    }

    private static void showDiff(String group, Set<String> local, Set<String> remote) {
        Sets.SetView<String> localNotRemote = Sets.difference(local, remote);
        Sets.SetView<String> remoteNotLocal = Sets.difference(remote, local);

        for (String user : localNotRemote) {
            System.out.println("add " + user + " to " + group);
        }
        for (String user : remoteNotLocal) {
            System.out.println("delete " + user + " from " + group);
        }
    }
}
