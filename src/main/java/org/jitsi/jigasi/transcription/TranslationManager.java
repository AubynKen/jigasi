/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.transcription;

import com.google.common.collect.ImmutableMap;
import org.jitsi.jigasi.JigasiBundleActivator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class manages the translations to be done by the transcriber.
 *
 * @author Praveen Kumar Gupta
 */
public class TranslationManager
    implements TranscriptionListener
{

    /**
     * Map of target languages for translating the transcriptions
     * to number of participants who need the particular language.
     */
    private final Map<String, Integer> languages = new HashMap<>();

    /**
     * List of listeners to be notified about a new TranslationResult.
     */
    private final List<TranslationResultListener> listeners
        = new ArrayList<>();

    /**
     * The translationService to be used for translations.
     */
    private final TranslationService translationService;

    /**
     *
     */
    public static final String P_NAME_ENABLE_PARTIAL_TRANSLATION = "hi"; // TODO: here

    /**
     * Whether interim transcripts should be translated
     */
    public static final String ENABLE_PARTIAL_TRANSLATION = JigasiBundleActivator.getConfigurationService()
        .getString(P_NAME_ENABLE_PARTIAL_TRANSLATION, null);

    /**
     * The subject(s)-object(o)-verb(v) order of language supported in Jitsi.
     * Used for determining whether it is appropriate to do translation on interim/partial transcripts.
     */
    private static final Map<String, String> languageWordOrder = Stream.of(new String[][] {
            {"af", "svo"},
            {"id", "svo"},
            {"ms", "svo"},
            {"ca", "svo"},
            {"cs", "svo"},
            {"da", "svo"},
            {"en", "svo"},
            {"es", "svo"},
            {"fr", "svo"},
            {"gl", "svo"},
            {"hr", "svo"},
            {"zu", "vo"}, // the subject is part of the verb compound
            {"is", "svo"},
            {"it", "svo"},
            {"hu", "svo"},
            {"nl", "svo"},
            {"nb", "svo"},
            {"pl", "svo"},
            {"pt", "svo"},
            {"ro", "svo"},
            {"sk", "svo"},
            {"sl", "sov"},
            {"fi", "svo"},
            {"sv", "svo"},
            {"vn", "svo"},
            {"tr", "sov"},
            {"el", "svo"},
            {"bg", "svo"},
            {"ru", "svo"},
            {"sr", "svo"},
            {"uk", "svo"},
            {"he", "svo"},
            {"ar", "vso"},
            {"fa", "sov"},
            {"hi", "sov"},
            {"th", "svo"},
            {"ko", "sov"},
            {"jp", "sov"},
            {"zh", "svo"},
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

    /**
     * Initializes the translationManager with a TranslationService
     * and adds the default target language to the list.
     *
     * @param service to be used by the TranslationManger
     */
    public TranslationManager(TranslationService service)
    {
        translationService = service;
    }

    /**
     * Adds a {@link TranslationResultListener} to the list of
     * listeners to be notified of a TranslationResult.
     *
     * @param listener to be added.
     */
    public void addListener(TranslationResultListener listener)
    {
        synchronized(listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Adds a language tag to the list of target languages for
     * translation or increments its count in the map if key exists.
     *
     * @param language to be added to the list
     */
    public void addLanguage(String language)
    {
        if (language == null || language.isEmpty())
            return;

        synchronized(languages)
        {
            languages.put(language, languages.getOrDefault(language, 0) + 1);
        }
    }

    /**
     * Decrements the language count in the map and removes the language if
     * no more participants need it.
     *
     * @param language whose count is to be decremented
     */
    public void removeLanguage(String language)
    {
        if (language == null)
            return;

        synchronized(languages)
        {
            int count = languages.get(language);

            if (count == 1)
            {
                languages.remove(language);
            }
            else
            {
                languages.put(language, count - 1);
            }
        }
    }

    /**
     * Translates the received {@link TranscriptionResult} into required languages
     * and returns a list of {@link TranslationResult}s.
     *
     * @param result the TranscriptionResult notified to the TranslationManager
     * @return list of TranslationResults
     */
    private List<TranslationResult> getTranslations(
        TranscriptionResult result)
    {
        ArrayList<TranslationResult> translatedResults
            = new ArrayList<>();
        Set<String> translationLanguages;

        synchronized (languages)
        {
            translationLanguages = languages.keySet();
        }

        Collection<TranscriptionAlternative> alternatives
            = result.getAlternatives();

        if (!alternatives.isEmpty())
        {
            for (String targetLanguage : translationLanguages)
            {
                String translatedText = translationService.translate(
                    alternatives.iterator().next().getTranscription(),
                    result.getParticipant().getSourceLanguage(),
                    targetLanguage);

                translatedResults.add(new TranslationResult(
                    result,
                    targetLanguage,
                    translatedText));
            }
        }

        return translatedResults;
    }

    /**
     * Translates the received {@link TranscriptionResult} into the
     * target languages and notifies the {@link TranslationResultListener}'s
     * of the {@link TranslationResult}s.
     *
     * @param result the result which has come in.
     */
    @Override
    public void notify(TranscriptionResult result)
    {
        if (!result.isInterim())
        {
            List<TranslationResult> translations
                = getTranslations(result);
            Iterable<TranslationResultListener> translationResultListeners;

            synchronized (listeners)
            {
                translationResultListeners = new ArrayList<>(listeners);
            }

            translationResultListeners.forEach(
                listener -> translations.forEach(listener::notify));
        }
    }

    @Override
    public void completed()
    {
        languages.clear();
    }

    @Override
    public void failed(FailureReason reason)
    {
        completed();
    }

    /**
     * Returns whether the source language and target language of translation have the same word order.
     * Word order is defined by the order in which the subject, object and verb are used in a phrase.
     * @param sourceLang the source language of to translate from
     * @param targetLang the target language to translate to
     * @return whether {@code sourceLang} have matching word order with {@code targetLang}
     */
    public boolean hasSameWordOrder(String sourceLang, String targetLang)
    {
        String sourceWordOrder = languageWordOrder.getOrDefault(sourceLang, null);
        String targetWordOrder = languageWordOrder.getOrDefault(targetLang, null);
        if (sourceWordOrder == null || targetWordOrder == null) return false;
        return sourceWordOrder.equals(targetWordOrder);
    }
}
