package io.spokestack.spokestack.asr;

/**
 * A schema class used for JSON metadata accompanying a keyword model. The only
 * item of interest in the metadata is the list of classes recognized by the
 * model.
 */
final class KeywordMetadata {
    private final KeywordClass[] classes;

    KeywordMetadata(KeywordClass[] classArr) {
        this.classes = classArr;
    }

    /**
     * @return the names of the classes associated with this model.
     */
    public String[] getClassNames() {
        String[] classNames = new String[this.classes.length];
        for (int i = 0; i < this.classes.length; i++) {
            classNames[i] = this.classes[i].name;
        }
        return classNames;
    }

    /**
     * A class of utterance recognized by a keyword model. It may contain many
     * utterances, but only the top-level class name is of interest to the
     * model.
     */
    static class KeywordClass {
        private final String name;

        KeywordClass(String className) {
            this.name = className;
        }
    }
}
