package com.github.erosb.hzyamlschema;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class HzYamlSchemaTest {

    public static final Schema SCHEMA = SchemaLoader.builder()
            .schemaJson(readJSONObject("/hazelcast-config-schema.json"))
            .build()
            .load().build();

    private static final JSONObject readJSONObject(String absPath) {
        return new JSONObject(new JSONTokener(HzYamlSchemaTest.class.getResourceAsStream(absPath)));
    }

    @Test
    void test() {
        try {
            SCHEMA.validate(readJSONObject("/hazelcast-full-example.json"));
        } catch (ValidationException e) {
            System.out.println(e.toJSON().toString(2));
            fail(e.getMessage());
        }
    }

    static Stream<Arguments> buildTestcases() {
        ConfigurationBuilder configuration = new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .setScanners(new ResourcesScanner());
        Reflections reflections = new Reflections(configuration);
        return reflections.getResources(Pattern.compile(".*\\.json")).stream()
                .filter(e -> e.startsWith("testcases"))
                .map(path -> buildArgs(path));
    }

    private static Arguments buildArgs(String path) {
        JSONObject testcase = readJSONObject("/" + path);
        Object error = testcase.get("error");
        if (error == JSONObject.NULL) {
            error = null;
        }
        String testName = path.substring("testcases/".length(), path.length() - 5);
        return Arguments.of(testName,
                testcase.getJSONObject("instance"),
                error);
    }

    private void sortCauses(JSONObject exc) {
        JSONArray causes = exc.optJSONArray("causingExceptions");
        if (causes != null) {
            List<JSONObject> causesList = new ArrayList<>(causes.length());
            for (int i = 0; i < causes.length(); ++i) {
                JSONObject item = causes.getJSONObject(i);
                sortCauses(item);
                causesList.add(item);
            }
            causesList.sort(Comparator.comparing(Object::toString));
            exc.put("causingExceptions", new JSONArray(causesList));
        }
    }

    @ParameterizedTest
    @MethodSource("buildTestcases")
    void runAllTests(String testName, JSONObject input, JSONObject expectedValidationError) {
        try {
            SCHEMA.validate(input);
            if (expectedValidationError != null) {
                fail("did not throw exception");
            }
        } catch (ValidationException e) {
            if (expectedValidationError == null) {
                System.err.println(e.toJSON().toString(2));
                fail("unexpected exception: " + e.getMessage());
            } else {
                sortCauses(expectedValidationError);
                JSONObject actualJson = e.toJSON();
                sortCauses(actualJson);
                assertEquals(expectedValidationError.toString(2), actualJson.toString(2));
            }
        }
    }

}
