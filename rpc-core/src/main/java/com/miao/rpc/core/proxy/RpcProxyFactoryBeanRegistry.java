package com.miao.rpc.core.proxy;

import com.miao.rpc.core.annotation.RpcReference;
import com.miao.rpc.core.client.RpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * 实现后置处理器BeanDefinitionRegistryPostProcessor，该类继承自BeanFactoryPostProcessor
 * BeanFactoryPostProcessor在容器实例化任何bean之前读取bean的定义(配置元数据)，并可以修改它。
 * BeanDefinitionRegistryPostProcessor可以让我们实现自定义的注册bean定义的逻辑
 *
 * 这里我的目的就是生成proxy的bean，客户端启动后那些服务暴露接口便有了实现类，就是这些proxy bean。
 * 下面就是自定义proxy bean的实现
 */
@Slf4j
public class RpcProxyFactoryBeanRegistry implements BeanDefinitionRegistryPostProcessor {
    private String basePackage;
    private RpcClient client;

    public RpcProxyFactoryBeanRegistry(String basePackage, RpcClient client) {
        this.basePackage = basePackage;
        this.client = client;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        log.info("正在添加动态代理类的FactoryBean");
        // 扫描工具类
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true); // 设置过滤条件，这里扫描所有
        Set<BeanDefinition> beanDefinitionSet = scanner.findCandidateComponents(basePackage); // 扫描指定路径下的类
        for (BeanDefinition beanDefinition : beanDefinitionSet) {
            log.info("扫描到的类的名称{}", beanDefinition.getBeanClassName());
            String beanClassName = beanDefinition.getBeanClassName(); // 得到class name
            Class<?> beanClass = null;
            try {
                beanClass = Class.forName(beanClassName); // 得到Class对象
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            Field[] fields = beanClass.getDeclaredFields(); // 获得该Class的所有字段
            for (Field field : fields) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                // RpcReference注解标识
                RpcReference reference = field.getAnnotation(RpcReference.class);
                Class<?> fieldClass = field.getType(); // 获取该标识下的类的类型，用于生成相应proxy
                if (reference != null) {
                    log.info("创建" + fieldClass.getName() + "的动态代理");
                    BeanDefinitionHolder holder = createBeanDefinition(fieldClass);
                    log.info("创建成功");
                    BeanDefinitionReaderUtils.registerBeanDefinition(holder, beanDefinitionRegistry);
                }
            }

        }
    }

    /**
     * 创建fieldClass类型的代理类proxy的BeanDefinition
     * @return
     */
    private BeanDefinitionHolder createBeanDefinition(Class<?> fieldClass) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RpcProxyFactoryBean.class);
        String className = fieldClass.getName();
        // bean的name首字母小写，spring通过它来注入
        String beanName = StringUtils.uncapitalize(className.substring(className.lastIndexOf('.')+1));
        // 给RpcProxyFactoryBean字段赋值
        builder.addPropertyValue("interfaceClass", fieldClass);
        builder.addPropertyValue("client", client);
        return new BeanDefinitionHolder(builder.getBeanDefinition(), beanName);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {

    }
}
