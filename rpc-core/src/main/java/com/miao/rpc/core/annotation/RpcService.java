package com.miao.rpc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.ANNOTATION_TYPE.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {

    Class<?> value();
}
