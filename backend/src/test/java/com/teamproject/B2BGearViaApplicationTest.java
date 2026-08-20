package com.teamproject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = B2BGearViaApplication.class)
class B2BGearViaApplicationTest {
    @Autowired ApplicationContext context;

    @Test void contextLoads() {}

    @Test
    void contextDoesNotRegisterPaymentOrSubscriptionBeans() {
        List<String> legacyBeans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(beanName -> {
                    Class<?> beanType = context.getType(beanName);
                    if (beanType == null || beanType.getPackageName() == null) {
                        return false;
                    }
                    String packageName = beanType.getPackageName();
                    return packageName.startsWith("com.teamproject.payment")
                            || packageName.startsWith("com.teamproject.subscription");
                })
                .toList();

        assertThat(legacyBeans).isEmpty();
    }
}
