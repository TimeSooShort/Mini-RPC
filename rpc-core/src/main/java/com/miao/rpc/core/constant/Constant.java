package com.miao.rpc.core.constant;

import java.nio.charset.Charset;

public class Constant {

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    public interface ZookeeperConstant {
        int ZK_SESSION_TIMEOUT = 5000;
        String ZK_REGISTRY_PATH = "/registry";
        String ZK_DATA_PATH = ZK_REGISTRY_PATH + "/data";
    }

    public interface LengthFieldConstant {
        int MAX_FRAME_LENGTH = 1024 * 1024;
        int LENGTH_FIELD_OFFSET = 0;
        int LENGTH_FIELD_LENGTH = 4;
        int LENGTH_ADJUSTMENT = 0;
        int INITIAL_BYTES_TO_STRIP = 4;
    }

    public enum ConnectionFailureStrategy {
        RETRY, CLOSE
    }
}
