package in.org.projecteka.hiu.dataprocessor;

import org.jeasy.random.EasyRandom;

public class TestBuilders {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static String string() {
        return easyRandom.nextObject(String.class);
    }
}
