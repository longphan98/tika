/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.language.translate.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.tika.exception.TikaException;
import org.apache.tika.language.translate.impl.YandexTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Generic Tests to ensure that the RSS library behaves as expected
 *
 * @author mark
 */

public class YandexTranslatorTest {

    private YandexTranslator translator;

    @BeforeEach
    public void setUp() throws Exception {
        this.translator = new YandexTranslator();
    }

    @Test
    public void test1EN_ES_Translation() {
        assumeTrue(translator.isAvailable());
        String inputText = "Hello World!!!";
        String expectedText = "Hola Mundo!!!";
        try {
            String transText = translator.translate(inputText, "en", "es");
            assertNotNull(transText, "Text not translated");
            assertEquals(expectedText, transText);
        } catch (TikaException | IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void test2UNDEFINED_DE_ES_Translation() {
        assumeTrue(translator.isAvailable());
        String inputText = "Guten Tag!!!";
        String expectedText = "Buen Día!!!";
        try {
            String transText = translator.translate(inputText, "es");
            assertNotNull(transText, "Text not translated");
            assertEquals(expectedText, transText);
        } catch (TikaException | IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void test3UNDEFINED_IT_EN_Translation() {
        assumeTrue(translator.isAvailable());
        String inputText = "Buona Sera!!!";
        String expectedText = "Good Evening!!!";
        try {
            String transText = translator.translate(inputText, "en");
            assertNotNull(transText, "Text not translated");
            assertEquals(expectedText, transText);
        } catch (TikaException | IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
