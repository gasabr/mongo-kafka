/*
 * Copyright 2008-present MongoDB, Inc.
 * Copyright 2017 Hans-Peter Grahsl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.grahsl.kafka.connect.mongodb.processor.field.projection;

import at.grahsl.kafka.connect.mongodb.MongoDbSinkConnectorConfig;
import at.grahsl.kafka.connect.mongodb.converter.SinkDocument;
import at.grahsl.kafka.connect.mongodb.processor.BlacklistKeyProjector;
import at.grahsl.kafka.connect.mongodb.processor.BlacklistValueProjector;
import at.grahsl.kafka.connect.mongodb.processor.WhitelistKeyProjector;
import at.grahsl.kafka.connect.mongodb.processor.WhitelistValueProjector;
import org.apache.kafka.connect.errors.DataException;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitPlatform.class)
class FieldProjectorTest {

    //flat doc field maps
    private static Map<Set<String>, BsonDocument> flatKeyFieldsMapBlacklist;
    private static Map<Set<String>, BsonDocument> flatKeyFieldsMapWhitelist;

    private static Map<Set<String>, BsonDocument> flatValueFieldsMapBlacklist;
    private static Map<Set<String>, BsonDocument> flatValueFieldsMapWhitelist;

    //nested doc field maps
    private static Map<Set<String>, BsonDocument> nestedKeyFieldsMapBlacklist;
    private static Map<Set<String>, BsonDocument> nestedKeyFieldsMapWhitelist;

    private static Map<Set<String>, BsonDocument> nestedValueFieldsMapBlacklist;
    private static Map<Set<String>, BsonDocument> nestedValueFieldsMapWhitelist;

    @BeforeAll
    static void setupFlatDocMaps() {
        // NOTE: FieldProjectors are currently implemented so that
        // a) when blacklisting: already present _id fields are never removed even if specified
        // b) when whitelisting: already present _id fields are always kept even if not specified

        //key projection settings
        BsonDocument keyDocument1 = BsonDocument.parse("{_id: 'ABC-123', myBoolean: true, myInt: 42, "
                + "myBytes: {$binary: 'QUJD', $type: '00'}, myArray: []}");
        BsonDocument keyDocument2 = BsonDocument.parse("{_id: 'ABC-123'}");
        BsonDocument keyDocument3 = BsonDocument.parse("{_id: 'ABC-123', myBytes: {$binary: 'QUJD', $type: '00'}, myArray: []}");
        BsonDocument keyDocument4 = BsonDocument.parse("{_id: 'ABC-123', myBoolean: true, myBytes: {$binary: 'QUJD', $type: '00'}, "
                + "myArray: []}");

        flatKeyFieldsMapBlacklist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), keyDocument1);
            put(new HashSet<>(singletonList("*")), keyDocument2);
            put(new HashSet<>(singletonList("**")), keyDocument2);
            put(new HashSet<>(singletonList("_id")), keyDocument1);
            put(new HashSet<>(asList("myBoolean", "myInt")), keyDocument3);
            put(new HashSet<>(asList("missing1", "unknown2")), keyDocument1);
        }};

        flatKeyFieldsMapWhitelist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), keyDocument2);
            put(new HashSet<>(singletonList("*")), keyDocument1);
            put(new HashSet<>(singletonList("**")), keyDocument1);
            put(new HashSet<>(asList("missing1", "unknown2")), keyDocument2);
            put(new HashSet<>(asList("myBoolean", "myBytes", "myArray")), keyDocument4);
        }};

        //value projection settings
        BsonDocument valueDocument1 = BsonDocument.parse("{_id: 'XYZ-789', myLong: {$numberLong: '42'}, "
                + "myDouble: 23.23, myString: 'BSON', myBytes: {$binary: 'eHl6', $type: '00'}, myArray: []}");
        BsonDocument valueDocument2 = BsonDocument.parse("{_id: 'XYZ-789'}");
        BsonDocument valueDocument3 = BsonDocument.parse("{_id: 'XYZ-789', myString: 'BSON', "
                + "myBytes: {$binary: 'eHl6', $type: '00'}, myArray: []}");
        BsonDocument valueDocument4 = BsonDocument.parse("{_id: 'XYZ-789', myDouble: 23.23, "
                + "myBytes: {$binary: 'eHl6', $type: '00'}, myArray: []}");

        flatValueFieldsMapBlacklist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), valueDocument1);
            put(new HashSet<>(singletonList("*")), valueDocument2);
            put(new HashSet<>(singletonList("**")), valueDocument2);
            put(new HashSet<>(singletonList("_id")), valueDocument1);
            put(new HashSet<>(asList("myLong", "myDouble")), valueDocument3);
            put(new HashSet<>(asList("missing1", "unknown2")), valueDocument1);
        }};

        flatValueFieldsMapWhitelist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), valueDocument2);
            put(new HashSet<>(singletonList("*")), valueDocument1);
            put(new HashSet<>(singletonList("**")), valueDocument1);
            put(new HashSet<>(asList("missing1", "unknown2")), valueDocument2);
            put(new HashSet<>(asList("myDouble", "myBytes", "myArray")), valueDocument4);
        }};
    }

    @BeforeAll
    static void setupNestedFieldLists() {

        // NOTE: FieldProjectors are currently implemented so that
        // a) when blacklisting: already present _id fields are never removed even if specified
        // and
        // b) when whitelisting: already present _id fields are always kept even if not specified

        BsonDocument keyDocument1 = BsonDocument.parse("{_id: 'ABC-123', myInt: 42, "
                + "subDoc1: {myBoolean: false}, subDoc2: {myString: 'BSON2'}}");
        BsonDocument keyDocument2 = BsonDocument.parse("{_id: 'ABC-123', "
                + "subDoc1: {myString: 'BSON1', myBoolean: false}, subDoc2: {myString: 'BSON2', myBoolean: true}}");
        BsonDocument keyDocument3 = BsonDocument.parse("{_id: 'ABC-123'}");
        BsonDocument keyDocument4 = BsonDocument.parse("{_id: 'ABC-123', subDoc1: {myBoolean: false}, subDoc2: {myBoolean: true}}");
        BsonDocument keyDocument5 = BsonDocument.parse("{_id: 'ABC-123', subDoc1: {}, subDoc2: {}}");
        BsonDocument keyDocument6 = BsonDocument.parse("{_id: 'ABC-123', myInt: 42, subDoc1: {}, subDoc2: {}}");

        nestedKeyFieldsMapBlacklist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(asList("_id", "subDoc1.myString", "subDoc2.myBoolean")), keyDocument1);
            put(new HashSet<>(singletonList("*")), keyDocument2);
            put(new HashSet<>(singletonList("**")), keyDocument3);
            put(new HashSet<>(singletonList("*.myString")), keyDocument4);
            put(new HashSet<>(singletonList("*.*")), keyDocument5);
        }};

        nestedKeyFieldsMapWhitelist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), keyDocument3);
            put(new HashSet<>(singletonList("*")), keyDocument6);
        }};


        // Value documents
        BsonDocument valueDocument1 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true}");
        BsonDocument valueDocument2 = BsonDocument.parse("{_id: 'XYZ-789'}");
        BsonDocument valueDocument3 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true, "
                + "subDoc1: {myFieldA: 'some text', myFieldB: 12.34}}");
        BsonDocument valueDocument4 = BsonDocument.parse("{_id: 'XYZ-789', "
                + "subDoc1: {subSubDoc: {myString: 'some text', myInt: 0, myBoolean: false}}, subDoc2: {}}");
        BsonDocument valueDocument5 = BsonDocument.parse("{_id: 'XYZ-789', "
                + "subDoc1: {subSubDoc: {myString: 'some text', myInt: 0, myBoolean: false}}, "
                + "subDoc2: {subSubDoc: {myBytes: {$binary: 'eHl6', $type: '00'}, "
                + "                      myArray: [{key: 'abc', value: 123}, {key: 'xyz', value: 987}]}}}");
        BsonDocument valueDocument6 = BsonDocument.parse("{_id: 'XYZ-789',"
                + "subDoc1: {myFieldA: 'some text', myFieldB: 12.34}, subDoc2: {myFieldA: 'some text', myFieldB: 12.34}}");
        BsonDocument valueDocument7 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true,"
                + "subDoc1: {subSubDoc: {myInt: 0, myBoolean: false}}, "
                + "subDoc2: {myFieldA: 'some text', myFieldB: 12.34, subSubDoc: {}}}");
        BsonDocument valueDocument8 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true, "
                + "subDoc2: {subSubDoc: {myArray: [{value: 123}, {value: 987}]}}}");
        BsonDocument valueDocument9 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true, subDoc1: {}, subDoc2: {}}");
        BsonDocument valueDocument10 = BsonDocument.parse("{_id: 'XYZ-789',"
                + "subDoc1: {myFieldA: 'some text', myFieldB: 12.34, subSubDoc: {myString: 'some text', myInt: 0, myBoolean: false}}, "
                + "subDoc2: {subSubDoc: {myArray: [{key: 'abc', value: 123}, {key: 'xyz', value: 987}]}}}");
        BsonDocument valueDocument11 = BsonDocument.parse("{_id: 'XYZ-789', myBoolean: true, "
                + "subDoc1: {myFieldA: 'some text', myFieldB: 12.34, subSubDoc: {myString: 'some text', myInt: 0, myBoolean: false}}, "
                + "subDoc2: {myFieldA: 'some text', myFieldB: 12.34, "
                + "          subSubDoc: {myBytes: {$binary: 'eHl6', $type: '00'}, "
                + "                      myArray: [{key: 'abc', value: 123}, {key: 'xyz', value: 987}]}}}");
        BsonDocument valueDocument12 = BsonDocument.parse("{_id: 'XYZ-789', "
                + "subDoc2: {subSubDoc: {myArray: [{key: 'abc'}, {key: 'xyz'}]}}}");

        nestedValueFieldsMapBlacklist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(asList("_id", "subDoc1", "subDoc2")), valueDocument1);
            put(new HashSet<>(singletonList("**")), valueDocument2);
            put(new HashSet<>(asList("subDoc1.subSubDoc", "subDoc2")), valueDocument3);
            put(new HashSet<>(asList("*", "subDoc1.*", "subDoc2.**")), valueDocument4);
            put(new HashSet<>(singletonList("*.*")), valueDocument5);
            put(new HashSet<>(singletonList("*.subSubDoc")), valueDocument6);
            put(new HashSet<>(asList("subDoc1.*.myString", "subDoc2.subSubDoc.*")), valueDocument7);
            put(new HashSet<>(asList("subDoc1", "subDoc2.myFieldA", "subDoc2.myFieldB",
                    "subDoc2.subSubDoc.myBytes", "subDoc2.subSubDoc.myArray.key")), valueDocument8);
        }};

        nestedValueFieldsMapWhitelist = new HashMap<Set<String>, BsonDocument>() {{
            put(new HashSet<>(), valueDocument2);
            put(new HashSet<>(singletonList("*")), valueDocument9);
            put(new HashSet<>(asList("subDoc1", "subDoc1.**", "subDoc2", "subDoc2.subSubDoc", "subDoc2.subSubDoc.myArray",
                    "subDoc2.subSubDoc.myArray.*")), valueDocument10);
            put(new HashSet<>(singletonList("**")), valueDocument11);
            put(new HashSet<>(asList("subDoc2", "subDoc2.subSubDoc", "subDoc2.subSubDoc.myArray", "subDoc2.subSubDoc.myArray.key")),
                    valueDocument12);
        }};
    }

    @TestFactory
    @DisplayName("testing different projector settings for flat structure")
    List<DynamicTest> testProjectorSettingsOnFlatStructure() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<Set<String>, BsonDocument> entry : flatKeyFieldsMapBlacklist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getKeyProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingBlacklistKeyProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentFlatStruct(), entry, BlacklistKeyProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : flatKeyFieldsMapWhitelist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getKeyProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingWhitelistKeyProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentFlatStruct(), entry, WhitelistKeyProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : flatValueFieldsMapBlacklist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getValueProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingBlacklistValueProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentFlatStruct(), entry, BlacklistValueProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : flatValueFieldsMapWhitelist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getValueProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingWhitelistValueProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentFlatStruct(), entry, WhitelistValueProjector.class, cfg));
        }

        return tests;
    }

    @TestFactory
    @DisplayName("testing different projector settings for nested structure")
    List<DynamicTest> testProjectorSettingsOnNestedStructure() {
        List<DynamicTest> tests = new ArrayList<>();

        for (Map.Entry<Set<String>, BsonDocument> entry : nestedKeyFieldsMapBlacklist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getKeyProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingBlacklistKeyProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentNestedStruct(), entry, BlacklistKeyProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : nestedKeyFieldsMapWhitelist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getKeyProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingWhitelistKeyProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentNestedStruct(), entry, WhitelistKeyProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : nestedValueFieldsMapBlacklist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getValueProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingBlacklistValueProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentNestedStruct(), entry, BlacklistValueProjector.class, cfg));
        }

        for (Map.Entry<Set<String>, BsonDocument> entry : nestedValueFieldsMapWhitelist.entrySet()) {
            MongoDbSinkConnectorConfig cfg = mock(MongoDbSinkConnectorConfig.class);
            when(cfg.getValueProjectionList("")).thenReturn(entry.getKey());
            when(cfg.isUsingWhitelistValueProjection("")).thenReturn(true);

            tests.add(buildDynamicTestFor(buildSinkDocumentNestedStruct(), entry, WhitelistValueProjector.class, cfg));
        }

        return tests;
    }

    private static DynamicTest buildDynamicTestFor(final SinkDocument doc, final Map.Entry<Set<String>, BsonDocument> entry,
                                                   final Class<? extends FieldProjector> clazz, final MongoDbSinkConnectorConfig cfg) {

        return dynamicTest(clazz.getSimpleName() + " with " + entry.getKey().toString(), () -> {
            FieldProjector fp = (FieldProjector) Class.forName(clazz.getName())
                    .getConstructor(MongoDbSinkConnectorConfig.class, String.class).newInstance(cfg, "");
            fp.process(doc, null);

            assertEquals(entry.getValue(), extractBsonDocument(doc, fp));
        });
    }

    private static BsonDocument extractBsonDocument(final SinkDocument doc, final FieldProjector which) {
        if (which instanceof BlacklistKeyProjector || which instanceof WhitelistKeyProjector) {
            return doc.getKeyDoc().orElseThrow(() -> new DataException("the needed BSON key doc was not present"));
        }
        if (which instanceof BlacklistValueProjector || which instanceof WhitelistValueProjector) {
            return doc.getValueDoc().orElseThrow(() -> new DataException("the needed BSON value was not present"));
        }
        throw new IllegalArgumentException("unexpected projector type " + which.getClass().getName());
    }

    private static SinkDocument buildSinkDocumentFlatStruct() {

        BsonDocument flatKey = BsonDocument.parse("{_id: 'ABC-123', myBoolean: true, myInt: 42, "
                + "myBytes: {$binary: 'QUJD', $type: '00'}, myArray: []}");
        BsonDocument flatValue = BsonDocument.parse("{ _id: 'XYZ-789', myLong: { $numberLong: '42' }, myDouble: 23.23, myString: 'BSON', "
                + "myBytes: { $binary: 'eHl6', $type: '00' }, myArray: [] }");
        return new SinkDocument(flatKey, flatValue);
    }

    private static SinkDocument buildSinkDocumentNestedStruct() {
        BsonDocument nestedKey = BsonDocument.parse("{ _id: 'ABC-123', myInt: 42, "
                + "subDoc1: { myString: 'BSON1', myBoolean: false }, subDoc2: { myString: 'BSON2', myBoolean: true } }");
        BsonDocument nestedValue = BsonDocument.parse("{ _id: 'XYZ-789', myBoolean: true, "
                + "subDoc1: { myFieldA: 'some text', myFieldB: 12.34, subSubDoc: { myString: 'some text', myInt: 0, myBoolean: false } }, "
                + "subDoc2: { myFieldA: 'some text', myFieldB: 12.34, "
                + "           subSubDoc: { myBytes: { $binary: 'eHl6', $type: '00' }, "
                + "                        myArray: [{ key: 'abc', value: 123 }, { key: 'xyz', value: 987 }] } } }");
        return new SinkDocument(nestedKey, nestedValue);
    }

}
