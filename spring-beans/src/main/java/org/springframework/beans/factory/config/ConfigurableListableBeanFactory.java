/*
 * Copyright 2002-2022 the original author or authors.
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.lang.Nullable;

import java.util.Iterator;

/**
 * Configuration interface to be implemented by most listable bean factories.
 * In addition to {@link ConfigurableBeanFactory}, it provides facilities to
 * analyze and modify bean definitions, and to pre-instantiate singletons.
 * 由大多数可列出的 bean 工厂实现的配置接口。除了继承自 ConfigurableBeanFactory 接口的功能之外，
 * 它还提供了分析和修改 bean 定义以及预实例化单例的能力。
 * <p>This subinterface of {@link org.springframework.beans.factory.BeanFactory}
 * is not meant to be used in normal application code: Stick to
 * {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * use cases. This interface is just meant to allow for framework-internal
 * plug'n'play even when needing access to bean factory configuration methods.
 * 这个 BeanFactory 的子接口并不打算用于普通应用代码中使用：通常情况下，请坚持使用 BeanFactory
 * 或 ListableBeanFactory。这个接口只是为了允许框架内部的即插即用，即使需要访问 bean 工厂配置方法也是如此。
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.context.support.AbstractApplicationContext#getBeanFactory()
 */
public interface ConfigurableListableBeanFactory
		extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 * 忽略给定的自动装配依赖项类型：例如 String。默认值为 none。
	 *
	 * @param type the dependency type to ignore
	 */
	void ignoreDependencyType(Class<?> type);

	/**
	 * Ignore the given dependency interface for autowiring.
	 * 为自动注入忽略给定的依赖接口。
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * 这通常被用于应用上下文注册以其他方式解析的依赖的场景，像通过 BeanFactoryAware 注册 BeanFactory
	 * 或通过 ApplicationContextAware 注册 ApplicationContext。
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * 默认 情况下，只有 BeanFactoryAware 接口会被忽略，如果要忽略其他类型，则对指定类型一一调用该方法。
	 * @param ifc the dependency interface to ignore
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	void ignoreDependencyInterface(Class<?> ifc);

	/**
	 * Register a special dependency type with corresponding autowired value.
	 * 使用对应的自动注入值，注册一个指定的依赖类型。
	 * <p>This is intended for factory/context references that are supposed
	 * to be autowirable but are not defined as beans in the factory:
	 * for example, a dependency of type ApplicationContext resolved to the
	 * ApplicationContext instance that the bean is living in.
	 * 该方法用于那些应该被自动注入却没有在工厂中定义为 bean 的工厂/上下文引用：如，
	 * ApplicationContext 类型的依赖项解析为 bean 所在的 ApplicationContext 实例。
	 * <p>Note: There are no such default types registered in a plain BeanFactory,
	 * not even for the BeanFactory interface itself.
	 * 原生 BeanFactory 中没有注册默认类型，甚至对于 BeanFactory 接口本身也不例外。
	 * @param dependencyType the dependency type to register. This will typically
	 * be a base interface such as BeanFactory, with extensions of it resolved
	 * as well if declared as an autowiring dependency (for example, ListableBeanFactory),
	 * as long as the given value actually implements the extended interface.
	 * @param autowiredValue the corresponding autowired value. This may also be an
	 * implementation of the {@link org.springframework.beans.factory.ObjectFactory}
	 * interface, which allows for lazy resolution of the actual target value.
	 */
	void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

	/**
	 * Determine whether the specified bean qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * 确定指定的 bean 是否有资格作为 autowire 候选者，以注入到声明了匹配类型的依赖的其他 bean 中。
	 * <p>This method checks ancestor factories as well.
	 * 该方法也会检查祖先工厂。
	 *
	 * @param beanName the name of the bean to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return whether the bean should be considered as autowire candidate
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 */
	boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException;

	/**
	 * Return the registered BeanDefinition for the specified bean, allowing access
	 * to its property values and constructor argument value (which can be
	 * modified during bean factory post-processing).
	 * 返回指定 bean 的已注册 BeanDefinition，允许访问其属性值和构造函数参数值（可以在 bean 工厂后置处理期间修改）。
	 * <p>A returned BeanDefinition object should not be a copy but the original
	 * definition object as registered in the factory. This means that it should
	 * be castable to a more specific implementation type, if necessary.
	 * 返回的 BeanDefinition 对象不应是副本，而应在工厂中注册的原始定义对象。这意味着如有必要，
	 * 它应该可以强制转换为更具体的实现类型。
	 * <p><b>NOTE:</b> This method does <i>not</i> consider ancestor factories.
	 * It is only meant for accessing local bean definitions of this factory.
	 * 该工厂不会从父工厂寻找，只访问当前工厂的本地 bean 定义。
	 *
	 * @param beanName the name of the bean
	 * @return the registered BeanDefinition
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * defined in this factory
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Return a unified view over all bean names managed by this factory.
	 * 返回此工厂管理的所有 bean 名称的统一视图。
	 * <p>Includes bean definition names as well as names of manually registered
	 * singleton instances, with bean definition names consistently coming first,
	 * analogous to how type/annotation specific retrieval of bean names works.
	 * 包括 bean 定义名称以及手动注册的单例实例的名称，其中 bean 定义名称始终排在前，类似于 bean
	 * 名称的类型/注解的特定检索的工作方式。
	 *
	 * @return the composite iterator for the bean names view
	 * @since 4.1.2
	 * @see #containsBeanDefinition
	 * @see #registerSingleton
	 * @see #getBeanNamesForType
	 * @see #getBeanNamesForAnnotation
	 */
	Iterator<String> getBeanNamesIterator();

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * 清除合并的 Bean 定义缓存，删除尚不符合完整元数据缓存条件的bean条目。
	 * <p>Typically triggered after changes to the original bean definitions,
	 * for example, after applying a {@link BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * 通常在更改原始 bean 定义后触发，例如，在应用 BeanFactoryPostProcessor 之后。请注意，此时已经创建的 bean 的元数据将被保留。
	 *
	 * @since 4.2
	 * @see #getBeanDefinition
	 * @see #getMergedBeanDefinition
	 */
	void clearMetadataCache();

	/**
	 * Freeze all bean definitions, signalling that the registered bean definitions
	 * will not be modified or post-processed any further.
	 * 冻结所有 bean 定义，表示已注册的 bean 定义不会再被修改或作任何后置处理。
	 * <p>This allows the factory to aggressively cache bean definition metadata
	 * going forward, after clearing the initial temporary metadata cache.
	 * 它允许工厂在清除初始临时元数据缓存后主动缓存 Bean 定义元数据。
	 *
	 * @see #clearMetadataCache()
	 * @see #isConfigurationFrozen()
	 */
	void freezeConfiguration();

	/**
	 * Return whether this factory's bean definitions are frozen,
	 * i.e. are not supposed to be modified or post-processed any further.
	 * 返回此工厂的 bean 定义是否被冻结，即不应进一步修改或后处理。
	 *
	 * @return {@code true} if the factory's configuration is considered frozen
	 * @see #freezeConfiguration()
	 */
	boolean isConfigurationFrozen();

	/**
	 * Ensure that all non-lazy-init singletons are instantiated, also considering
	 * {@link org.springframework.beans.factory.FactoryBean FactoryBeans}.
	 * Typically invoked at the end of factory setup, if desired.
	 * 确保所有非懒加载的单例被初始化，还要考虑 FactoryBean。如果需要，通常在工厂设置结束时调用。
	 * @throws BeansException if one of the singleton beans could not be created.
	 * Note: This may have left the factory with some beans already initialized!
	 * Call {@link #destroySingletons()} for full cleanup in this case.
	 * @see #destroySingletons()
	 */
	void preInstantiateSingletons() throws BeansException;

}
