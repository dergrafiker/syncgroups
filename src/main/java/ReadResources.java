import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class ReadResources {
    static List<String> readLinesFromExternalFile(String resourceName) throws IOException {
        try (InputStream resourceAsStream = Main.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceAsStream == null) {
                throw new IllegalArgumentException(resourceName + " could not be found");
            }
            return new BufferedReader(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    static Map<String, Set<String>> readIntendedGroupToUserMapFromExternalFile(String resourceName) throws IOException {
        return readLinesFromExternalFile(resourceName).stream()
                .map(String::toLowerCase)
                .map(line -> line.split(":"))
                .collect(groupingBy(groupAndUserEmail -> groupAndUserEmail[0],
                        mapping(strings -> strings[1], toSet())
                ));

    }

    static Map<String, Set<String>> readCurrentGroupToUserMapFromRemoteCSV(String resourceName) throws IOException {
        URL resource = Main.class.getResource(resourceName);

        CSVParser records = CSVFormat.RFC4180
                .withFirstRecordAsHeader()
                .parse(new InputStreamReader(resource.openStream()));

        return records.getRecords().stream()
                .collect(Collectors.groupingBy(record -> StringUtils.substringBefore(record.get("group").toLowerCase(),"@"),
                        mapping(record -> record.get("email").toLowerCase(),
                                toSet()))
                );
    }
}
