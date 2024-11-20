/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.config;

import org.springframework.lang.Nullable;

import java.util.function.Consumer;

/**
 * Interface that defines a registry for shared bean instances.
 * Can be implemented by {@link org.springframework.beans.factory.BeanFactory}
 * implementations in order to expose their singleton management facility
 * in a uniform manner.
 * 单例注册表，即为共享 Bean 实例定义注册表的接口。可被 BeanFactory 的子类实现，以便用统一的方式暴露其单例管理能力。
 *
 * <p>The {@link ConfigurableBeanFactory} interface extends this interface.
 * ConfigurableBeanFactory 继承了该接口。
 *
 * @author Juergen Hoeller
 * @see ConfigurableBeanFactory
 * @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
 * @see org.springframework.beans.factory.support.AbstractBeanFactory
 * @since 2.0
 */
public interface SingletonBeanRegistry {

	/**
	 * Register the given existing object as singleton in the bean registry,
	 * under the given bean name.
	 * 向 bean 注册表中以给定名字将给定对象注册为单例。
	 * <p>The given instance is supposed to be fully initialized; the registry
	 * will not perform any initialization callbacks (in particular, it won't
	 * call InitializingBean's {@code afterPropertiesSet} method).
	 * The given instance will not receive any destruction callbacks
	 * (like DisposableBean's {@code destroy} method) either.
	 * 给定实例应完全初始化；注册表不会执行任何初始化回调（特别是，它不会调用 InitializingBean 的 afterPropertiesSet()。
	 * <p>When running within a full BeanFactory: <b>Register a bean definition
	 * instead of an existing instance if your bean is supposed to receive
	 * initialization and/or destruction callbacks.</b>
	 * 当在完整的 BeanFactory 中运行时：如果 bean 应该接收初始化和/或销毁回调，则应注册 bean 定义，而不是一个已存在的实例。
	 * <p>Typically invoked during registry configuration, but can also be used
	 * for runtime registration of singletons. As a consequence, a registry
	 * implementation should synchronize singleton access; it will have to do
	 * this anyway if it supports a BeanFactory's lazy initialization of singletons.
	 * 通常在注册表配置期间调用，但也可用于单例的运行时注册。因此，注册表实现应同步单例访问；如果它支持 BeanFactory 的单例延迟初始化，
	 * 那么它无论如何都必须这样做。
	 *
	 * @param beanName        the name of the bean
	 * @param singletonObject the existing singleton object
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.DisposableBean#destroy
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition
	 */
	void registerSingleton(String beanName, Object singletonObject);

	/**
	 * Add a callback to be triggered when the specified singleton becomes available
	 * in the bean registry.
	 * 添加一个回调，当指定的单例在 bean 注册表中可用时触发。
	 *
	 * @param beanName          the name of the bean
	 * @param singletonConsumer a callback for reacting to the availability of the freshly
	 *                          registered/created singleton instance (intended for follow-up steps before the bean is
	 *                          actively used by other callers, not for modifying the given singleton instance itself)
	 * @since 6.2
	 */
	void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer);

	/**
	 * Return the (raw) singleton object registered under the given name.
	 * 返回指定的（原生）单例对象。
	 * <p>Only checks already instantiated singletons; does not return an Object
	 * for singleton bean definitions which have not been instantiated yet.
	 * 仅检查已经初始化的单例；对于尚未实例化的单例 bean 定义，不返回对象。
	 * <p>The main purpose of this method is to access manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to access a singleton
	 * defined by a bean definition that already been created, in a raw fashion.
	 * 该方法的主要目的是访问人工注册的单例（参见 registerSingleton()）。也可以用原生方式访问已创建的 bean 定义单例。
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before obtaining the singleton instance.
	 * 注意：该查找方法意识不到 FactoryBean 前缀或别名。在获取单例实例前，应先解析 bean 名为规范名。
	 *
	 * @param beanName the name of the bean to look for
	 * @return the registered singleton object, or {@code null} if none found
	 * @see ConfigurableListableBeanFactory#getBeanDefinition
	 */
	@Nullable
	Object getSingleton(String beanName);

	/**
	 * Check if this registry contains a singleton instance with the given name.
	 * 检查注册表是否包含给定单例。
	 * <p>Only checks already instantiated singletons; does not return {@code true}
	 * for singleton bean definitions which have not been instantiated yet.
	 * 仅检查已经初始化的单例；对于尚未实例化的单例 bean 定义，返回 false。
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check whether a
	 * singleton defined by a bean definition has already been created.
	 * 该方法的主要目的是检查人工注册的单例（参见 registerSingleton()）。也可以用于检查已创建的 bean 定义单例。
	 * <p>To check whether a bean factory contains a bean definition with a given name,
	 * use ListableBeanFactory's {@code containsBeanDefinition}. Calling both
	 * {@code containsBeanDefinition} and {@code containsSingleton} answers
	 * whether a specific bean factory contains a local bean instance with the given name.
	 * 如要检查 bean 工厂是否包含给定名称的 bean 定义，请使用 ListableBeanFactory 的 containsBeanDefinition()。
	 * 同时调用 containsBeanDefinition() 和 containsSingleton() 来判断指定 bean 工厂是否包含给定名称的本地 bean 实例。
	 * <p>Use BeanFactory's {@code containsBean} for general checks whether the
	 * factory knows about a bean with a given name (whether manually registered singleton
	 * instance or created by bean definition), also checking ancestor factories.
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before checking the singleton status.
	 * 注意：该查找方法意识不到 FactoryBean 前缀或别名。在获取单例实例前，应先解析 bean 名为规范名。
	 *
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a singleton instance with the given name
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 * @see org.springframework.beans.factory.BeanFactory#containsBean
	 */
	boolean containsSingleton(String beanName);

	/**
	 * Return the names of singleton beans registered in this registry.
	 * 返回注册表中已注册的单例名列表。
	 * <p>Only checks already instantiated singletons; does not return names
	 * for singleton bean definitions which have not been instantiated yet.
	 * 仅检查已经初始化的单例；对于尚未实例化的单例 bean 定义，不会返回其名字。
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check which singletons
	 * defined by a bean definition have already been created.
	 * 该方法的主要目的是检查人工注册的单例（参见 registerSingleton()）。也可以用于检查已创建的 bean 定义单例。
	 *
	 * @return the list of names as a String array (never {@code null})
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionNames
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionNames
	 */
	String[] getSingletonNames();

	/**
	 * Return the number of singleton beans registered in this registry.
	 * 返回注册表中已注册的单例个数。
	 * <p>Only checks already instantiated singletons; does not count
	 * singleton bean definitions which have not been instantiated yet.
	 * 仅检查已经初始化的单例；对于尚未实例化的单例 bean 定义，不会统计在内。
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to count the number of
	 * singletons defined by a bean definition that have already been created.
	 * 该方法的主要目的是检查人工注册的单例（参见 registerSingleton()）。也可以用于计数已创建的 bean 定义单例。
	 *
	 * @return the number of singleton beans
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionCount
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionCount
	 */
	int getSingletonCount();

	/**
	 * Return the singleton mutex used by this registry (for external collaborators).
	 * 返回此注册表使用的单例互斥锁（适用于外部协作者）。
	 *
	 * @return the mutex object (never {@code null})
	 * @since 4.2
	 * @deprecated as of 6.2, in favor of lenient singleton locking
	 * (with this method returning an arbitrary object to lock on)
	 */
	@Deprecated(since = "6.2")
	Object getSingletonMutex();

}
