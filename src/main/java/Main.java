import com.google.common.collect.Sets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class Main {
    public static void main(String[] args) throws IOException {
        Map<String, Set<String>> localMapping = ReadResources.readMemberMapFromExternalFile("mapping");

        Map<String, Set<String>> fromRemote = getMemberMapFromRemoteCSV("out.csv", args[1]);

        Set<String> allGroups = new HashSet<>();
        allGroups.addAll(localMapping.keySet());
        allGroups.addAll(fromRemote.keySet());

        for (String group : allGroups) {
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

        String catchAll = args[0];

        Set<String> allMailsInLocalMapping = localMapping.values().stream().flatMap(Collection::stream).collect(toSet());
        Set<String> allMailsFromRemote = fromRemote.get(catchAll);

        showDiff(catchAll, allMailsInLocalMapping, allMailsFromRemote);
    }

    private static Map<String, Set<String>> getMemberMapFromRemoteCSV(String resourceName, String groupReplacement) throws IOException {
        URL resource = Main.class.getResource(resourceName);

        CSVParser records = CSVFormat.RFC4180
                .withFirstRecordAsHeader()
                .parse(new InputStreamReader(resource.openStream()));

        return records.getRecords().stream()
                .collect(Collectors.groupingBy(record -> record.get("group").replace(groupReplacement, ""),
                        mapping(record -> record.get("email").toLowerCase(),
                                toSet()))
                );
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
