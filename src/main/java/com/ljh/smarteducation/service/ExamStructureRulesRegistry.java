package com.ljh.smarteducation.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry that holds static structural rules for different exam templates.
 *
 * Phase A-3: only configuration for Template_Gaokao_Eng_A is provided here
 * and the rules are NOT yet wired into any business logic.
 */
public final class ExamStructureRulesRegistry {

    private static final List<ExamStructureRule> GAOKAO_ENGLISH_A_RULES;
    private static final List<ExamStructureRule> CET4_GENERIC_RULES;
    private static final List<ExamStructureRule> CET6_GENERIC_RULES;

    static {
        List<ExamStructureRule> rules = new ArrayList<>();

        // I. Listening Comprehension - Section A: 短对话
        ExamStructureRule listeningA = new ExamStructureRule();
        listeningA.setId("LISTENING_SECTION_A");
        listeningA.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        listeningA.setOrderInExam(1);
        listeningA.setMinQuestionCount(8);   // 大致 10 题，允许 8-12
        listeningA.setMaxQuestionCount(12);
        listeningA.setAllowedQuestionTypes(List.of("LISTENING", "MULTIPLE_CHOICE"));
        listeningA.setSectionKeywords(List.of(
                "Listening Comprehension",
                "Section A",
                "short conversations",
                "Directions"
        ));
        listeningA.setInstructionKeywords(List.of(
                "question",
                "A.", "B.", "C.", "D."
        ));
        rules.add(listeningA);

        // I. Listening Comprehension - Section B: 短文 + 长对话
        ExamStructureRule listeningB = new ExamStructureRule();
        listeningB.setId("LISTENING_SECTION_B");
        listeningB.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        listeningB.setOrderInExam(2);
        listeningB.setMinQuestionCount(8);
        listeningB.setMaxQuestionCount(12);
        listeningB.setAllowedQuestionTypes(List.of("LISTENING", "MULTIPLE_CHOICE"));
        listeningB.setSectionKeywords(List.of(
                "Listening Comprehension",
                "Section B",
                "passage",
                "conversation"
        ));
        listeningB.setInstructionKeywords(List.of(
                "read twice",
                "question",
                "A.", "B.", "C.", "D."
        ));
        rules.add(listeningB);

        // II. Grammar and Vocabulary - Section A: 词形填空
        ExamStructureRule grammarA = new ExamStructureRule();
        grammarA.setId("GRAMMAR_SECTION_A");
        grammarA.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        grammarA.setOrderInExam(3);
        grammarA.setMinQuestionCount(8);
        grammarA.setMaxQuestionCount(12);
        grammarA.setAllowedQuestionTypes(List.of("CLOZE_WORD_FORM", "GRAMMAR"));
        grammarA.setSectionKeywords(List.of(
                "Grammar and Vocabulary",
                "Section A",
                "fill in the blanks",
                "proper form of the given word"
        ));
        grammarA.setInstructionKeywords(List.of(
                "blanks",
                "given word"
        ));
        rules.add(grammarA);

        // II. Grammar and Vocabulary - Section B: 词汇选填
        ExamStructureRule grammarB = new ExamStructureRule();
        grammarB.setId("GRAMMAR_SECTION_B");
        grammarB.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        grammarB.setOrderInExam(4);
        grammarB.setMinQuestionCount(8);
        grammarB.setMaxQuestionCount(12);
        grammarB.setAllowedQuestionTypes(List.of("CLOZE_VOCAB", "GRAMMAR"));
        grammarB.setSectionKeywords(List.of(
                "Grammar and Vocabulary",
                "Section B",
                "fill in each blank",
                "from the box",
                "each word used once"
        ));
        grammarB.setInstructionKeywords(List.of(
            "box",
            "choices"
        ));
        rules.add(grammarB);

        // III. Reading Comprehension - Section A: 单篇填空
        ExamStructureRule readingA = new ExamStructureRule();
        readingA.setId("READING_SECTION_A");
        readingA.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        readingA.setOrderInExam(5);
        readingA.setMinQuestionCount(10);
        readingA.setMaxQuestionCount(20); // 通常 15 题左右
        readingA.setAllowedQuestionTypes(List.of("CLOZE", "READING"));
        readingA.setSectionKeywords(List.of(
                "Reading Comprehension",
                "Section A",
                "blank",
                "A.", "B.", "C.", "D."
        ));
        readingA.setInstructionKeywords(List.of(
                "passage",
                "choose the best answer"
        ));
        rules.add(readingA);

        // III. Reading Comprehension - Section B: 多篇阅读理解（含小分段 A/B/C）
        ExamStructureRule readingB = new ExamStructureRule();
        readingB.setId("READING_SECTION_B");
        readingB.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        readingB.setOrderInExam(6);
        readingB.setMinQuestionCount(10);
        readingB.setMaxQuestionCount(20);
        readingB.setAllowedQuestionTypes(List.of("READING", "MULTIPLE_CHOICE"));
        readingB.setSectionKeywords(List.of(
                "Reading Comprehension",
                "Section B",
                "passage",
                "Questions",
                "A.", "B.", "C.", "D."
        ));
        readingB.setInstructionKeywords(List.of(
                "answer the questions",
                "according to the passage"
        ));
        rules.add(readingB);

        // III. Reading Comprehension - Section C: 衔接填句
        ExamStructureRule readingC = new ExamStructureRule();
        readingC.setId("READING_SECTION_C");
        readingC.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        readingC.setOrderInExam(7);
        readingC.setMinQuestionCount(3);
        readingC.setMaxQuestionCount(6); // 通常 4 题
        readingC.setAllowedQuestionTypes(List.of("SENTENCE_COMPLETION", "READING"));
        readingC.setSectionKeywords(List.of(
                "Reading Comprehension",
                "Section C",
                "Complete the following passage",
                "sentences in the box"
        ));
        readingC.setInstructionKeywords(List.of(
                "box",
                "choose from the sentences"
        ));
        rules.add(readingC);

        // IV. Summary Writing
        ExamStructureRule summary = new ExamStructureRule();
        summary.setId("SUMMARY_WRITING");
        summary.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        summary.setOrderInExam(8);
        summary.setMinQuestionCount(1);
        summary.setMaxQuestionCount(1);
        summary.setAllowedQuestionTypes(List.of("WRITING"));
        summary.setSectionKeywords(List.of(
                "Summary Writing",
                "Summarize",
                "main idea"
        ));
        summary.setInstructionKeywords(List.of(
                "no more than",
                "words"
        ));
        rules.add(summary);

        // V. Translation
        ExamStructureRule translation = new ExamStructureRule();
        translation.setId("TRANSLATION");
        translation.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        translation.setOrderInExam(9);
        translation.setMinQuestionCount(3); // 通常 3-4 句
        translation.setMaxQuestionCount(5);
        translation.setAllowedQuestionTypes(List.of("TRANSLATION"));
        translation.setSectionKeywords(List.of(
                "Translation",
                "Translate",
                "into English"
        ));
        translation.setInstructionKeywords(List.of(
                "using the words in brackets",
                "using the words given in brackets"
        ));
        rules.add(translation);

        // VI. Guided Writing
        ExamStructureRule guided = new ExamStructureRule();
        guided.setId("GUIDED_WRITING");
        guided.setTemplate(ExamTemplate.GAOKAO_ENGLISH_A);
        guided.setOrderInExam(10);
        guided.setMinQuestionCount(1);
        guided.setMaxQuestionCount(1);
        guided.setAllowedQuestionTypes(List.of("WRITING"));
        guided.setSectionKeywords(List.of(
                "Guided Writing",
                "Write an English composition"
        ));
        guided.setInstructionKeywords(List.of(
                "120-150 words",
                "according to the instructions"
        ));
        rules.add(guided);

        GAOKAO_ENGLISH_A_RULES = Collections.unmodifiableList(rules);

        // ---------------- CET-4 coarse-grained rules ----------------
        List<ExamStructureRule> cet4 = new ArrayList<>();

        ExamStructureRule cet4Writing = new ExamStructureRule();
        cet4Writing.setId("CET4_WRITING");
        cet4Writing.setTemplate(ExamTemplate.Template_CET4_Generic);
        cet4Writing.setOrderInExam(1);
        cet4Writing.setMinQuestionCount(1);
        cet4Writing.setMaxQuestionCount(2);
        cet4Writing.setAllowedQuestionTypes(List.of("WRITING"));
        cet4Writing.setSectionKeywords(List.of(
                "Writing",
                "Part I",
                "作文",
                "composition"
        ));
        cet4Writing.setInstructionKeywords(List.of(
                "Write an essay",
                "in no less than",
                "words"
        ));
        cet4.add(cet4Writing);

        ExamStructureRule cet4Listening = new ExamStructureRule();
        cet4Listening.setId("CET4_LISTENING");
        cet4Listening.setTemplate(ExamTemplate.Template_CET4_Generic);
        cet4Listening.setOrderInExam(2);
        cet4Listening.setMinQuestionCount(15); // very loose ranges
        cet4Listening.setMaxQuestionCount(35);
        cet4Listening.setAllowedQuestionTypes(List.of("LISTENING", "MULTIPLE_CHOICE"));
        cet4Listening.setSectionKeywords(List.of(
                "Listening Comprehension",
                "Part II",
                "Section A",
                "Section B",
                "recorded"
        ));
        cet4Listening.setInstructionKeywords(List.of(
                "listen to the",
                "choose the best answer",
                "A.", "B.", "C.", "D."
        ));
        cet4.add(cet4Listening);

        ExamStructureRule cet4Reading = new ExamStructureRule();
        cet4Reading.setId("CET4_READING");
        cet4Reading.setTemplate(ExamTemplate.Template_CET4_Generic);
        cet4Reading.setOrderInExam(3);
        cet4Reading.setMinQuestionCount(15);
        cet4Reading.setMaxQuestionCount(35);
        cet4Reading.setAllowedQuestionTypes(List.of("READING", "MULTIPLE_CHOICE"));
        cet4Reading.setSectionKeywords(List.of(
                "Reading Comprehension",
                "Part III",
                "Passage",
                "Questions"
        ));
        cet4Reading.setInstructionKeywords(List.of(
                "answer the questions",
                "according to the passage"
        ));
        cet4.add(cet4Reading);

        ExamStructureRule cet4Translation = new ExamStructureRule();
        cet4Translation.setId("CET4_TRANSLATION");
        cet4Translation.setTemplate(ExamTemplate.Template_CET4_Generic);
        cet4Translation.setOrderInExam(4);
        cet4Translation.setMinQuestionCount(1);
        cet4Translation.setMaxQuestionCount(10);
        cet4Translation.setAllowedQuestionTypes(List.of("TRANSLATION"));
        cet4Translation.setSectionKeywords(List.of(
                "Translation",
                "Part IV",
                "translate the following",
                "into Chinese",
                "into English"
        ));
        cet4Translation.setInstructionKeywords(List.of(
                "translate",
                "paragraph",
                "sentences"
        ));
        cet4.add(cet4Translation);

        CET4_GENERIC_RULES = Collections.unmodifiableList(cet4);

        // ---------------- CET-6 coarse-grained rules ----------------
        List<ExamStructureRule> cet6 = new ArrayList<>();

        ExamStructureRule cet6Writing = new ExamStructureRule();
        cet6Writing.setId("CET6_WRITING");
        cet6Writing.setTemplate(ExamTemplate.Template_CET6_Generic);
        cet6Writing.setOrderInExam(1);
        cet6Writing.setMinQuestionCount(1);
        cet6Writing.setMaxQuestionCount(2);
        cet6Writing.setAllowedQuestionTypes(List.of("WRITING"));
        cet6Writing.setSectionKeywords(List.of(
                "Writing",
                "Part I",
                "作文",
                "composition"
        ));
        cet6Writing.setInstructionKeywords(List.of(
                "Write an essay",
                "no less than",
                "words"
        ));
        cet6.add(cet6Writing);

        ExamStructureRule cet6Listening = new ExamStructureRule();
        cet6Listening.setId("CET6_LISTENING");
        cet6Listening.setTemplate(ExamTemplate.Template_CET6_Generic);
        cet6Listening.setOrderInExam(2);
        cet6Listening.setMinQuestionCount(15);
        cet6Listening.setMaxQuestionCount(35);
        cet6Listening.setAllowedQuestionTypes(List.of("LISTENING", "MULTIPLE_CHOICE"));
        cet6Listening.setSectionKeywords(List.of(
                "Listening Comprehension",
                "Part II",
                "Section A",
                "Section B",
                "recorded"
        ));
        cet6Listening.setInstructionKeywords(List.of(
                "listen to the",
                "choose the best answer",
                "A.", "B.", "C.", "D."
        ));
        cet6.add(cet6Listening);

        ExamStructureRule cet6Reading = new ExamStructureRule();
        cet6Reading.setId("CET6_READING");
        cet6Reading.setTemplate(ExamTemplate.Template_CET6_Generic);
        cet6Reading.setOrderInExam(3);
        cet6Reading.setMinQuestionCount(15);
        cet6Reading.setMaxQuestionCount(35);
        cet6Reading.setAllowedQuestionTypes(List.of("READING", "MULTIPLE_CHOICE"));
        cet6Reading.setSectionKeywords(List.of(
                "Reading Comprehension",
                "Part III",
                "Passage",
                "Questions"
        ));
        cet6Reading.setInstructionKeywords(List.of(
                "answer the questions",
                "according to the passage"
        ));
        cet6.add(cet6Reading);

        ExamStructureRule cet6Translation = new ExamStructureRule();
        cet6Translation.setId("CET6_TRANSLATION");
        cet6Translation.setTemplate(ExamTemplate.Template_CET6_Generic);
        cet6Translation.setOrderInExam(4);
        cet6Translation.setMinQuestionCount(1);
        cet6Translation.setMaxQuestionCount(10);
        cet6Translation.setAllowedQuestionTypes(List.of("TRANSLATION"));
        cet6Translation.setSectionKeywords(List.of(
                "Translation",
                "Part IV",
                "translate the following",
                "into Chinese",
                "into English"
        ));
        cet6Translation.setInstructionKeywords(List.of(
                "translate",
                "paragraph",
                "sentences"
        ));
        cet6.add(cet6Translation);

        CET6_GENERIC_RULES = Collections.unmodifiableList(cet6);
    }

    private ExamStructureRulesRegistry() {
        // utility class
    }

    /**
     * Returns the structural rules for the given template.
     *
     * Phase A-3: currently only GAOKAO_ENGLISH_A has concrete rules.
     */
    public static List<ExamStructureRule> getRulesForTemplate(ExamTemplate template) {
        if (template == ExamTemplate.GAOKAO_ENGLISH_A) {
            return GAOKAO_ENGLISH_A_RULES;
        }
        if (template == ExamTemplate.Template_CET4_Generic) {
            return CET4_GENERIC_RULES;
        }
        if (template == ExamTemplate.Template_CET6_Generic) {
            return CET6_GENERIC_RULES;
        }
        return Collections.emptyList();
    }
}
