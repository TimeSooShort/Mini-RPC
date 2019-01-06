package com.miao.rpc.core.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class PropertityUtil {
    private static Properties props;

    static {
        loadProps();
    }

    private static void loadProps() {
        log.info("开始加载properties文件内容");
        props = new Properties();
        InputStream in = null;
        try {
            in = PropertityUtil.class.getClassLoader().getResourceAsStream("application.properties");
            props.load(in);
        } catch (IOException e) {
            log.error("出现IO异常");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("rpc.properties文件流关闭出现异常");
            }
        }
        log.info("properties加载完毕，内容为:{}", props);
    }

    public static String getProperty(String key) {
        if (props == null) loadProps();
        return props.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        if (props == null) loadProps();
        return props.getProperty(key, defaultValue);
    }
}
