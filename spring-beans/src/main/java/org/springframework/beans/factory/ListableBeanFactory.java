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

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

/**
 * Extension of the {@link BeanFactory} interface to be implemented by bean factories
 * that can enumerate all their bean instances, rather than attempting bean lookup
 * by name one by one as requested by clients. BeanFactory implementations that
 * preload all their bean definitions (such as XML-based factories) may implement
 * this interface.
 * BeanFactory 接口的扩展，由 bean 工厂类实现，可枚举所有 bean 实例，而不是根据客户端的要求尝试逐个
 * 尝试按名称查找 bean。预加载所有 bean 定义的 BeanFactory（例如基于 XML 的工厂）实现可以实现此接口。
 *
 * <p>If this is a {@link HierarchicalBeanFactory}, the return values will <i>not</i>
 * take any BeanFactory hierarchy into account, but will relate only to the beans
 * defined in the current factory. Use the {@link BeanFactoryUtils} helper class
 * to consider beans in ancestor factories too.
 * 如果实现类是一个 HierarchicalBeanFactory，则返回值将不考虑任何 BeanFactory 层次结构，而只与当前
 * 工厂中定义的 bean 相关。使用 BeanFactoryUtils 辅助类以检索祖先工厂中的 bean。
 *
 * <p>The methods in this interface will just respect bean definitions of this factory.
 * They will ignore any singleton beans that have been registered by other means like
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}'s
 * {@code registerSingleton} method, with the exception of
 * {@code getBeanNamesForType} and {@code getBeansOfType} which will check
 * such manually registered singletons too. Of course, BeanFactory's {@code getBean}
 * does allow transparent access to such special beans as well. However, in typical
 * scenarios, all beans will be defined by external bean definitions anyway, so most
 * applications don't need to worry about this differentiation.
 * 此接口中的方法将仅遵循此工厂的 bean 定义。忽略任何通过其他方式注册的单例 bean，如 ConfigurableBeanFactory
 * 的 registerSingleton() 方法，但 getBeanNamesForType() 和 getBeansOfType() 除外，它们也会检查此类手动注册的单例。
 * 当然，BeanFactory 的 getBean() 也允许对这种特殊 bean 进行透明访问。
 * 但是，在典型场景中，所有 bean 都将由外部 bean definition 定义，因此大多数应用程序不需要担心这种差异。
 *
 * <p><b>NOTE:</b> With the exception of {@code getBeanDefinitionCount}
 * and {@code containsBeanDefinition}, the methods in this interface
 * are not designed for frequent invocation. Implementations may be slow.
 * 除了 getBeanDefinitionCount() 和 containsBeanDefinition() 之外，此接口中的方法不是为频繁调用而设计的。具体实现可能会很慢。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16 April 2001
 * @see HierarchicalBeanFactory
 * @see BeanFactoryUtils
 */
public interface ListableBeanFactory extends BeanFactory {

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * 检查此 bean 工厂是否包含具有给定名称的 bean 定义。
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * 不考虑此工厂可能参与的任何层次结构，并忽略任何已通过 bean 定义以外的方式注册的单例 bean。
	 *
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 */
	boolean containsBeanDefinition(String beanName);

	/**
	 * Return the number of beans defined in the factory.
	 * 返回工厂中定义的 bean 数量。
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * 不考虑此工厂可能参与的任何层次结构，并忽略任何已通过 bean 定义以外的方式注册的单例 bean。
	 *
	 * @return the number of beans defined in the factory
	 */
	int getBeanDefinitionCount();

	/**
	 * Return the names of all beans defined in this factory.
	 * 返回此工厂中定义的所有 bean 的名称。
	 * <p>Does not consider any hierarchy this factory may participate in,
	 * and ignores any singleton beans that have been registered by
	 * other means than bean definitions.
	 * 不考虑此工厂可能参与的任何层次结构，并忽略任何已通过 bean 定义以外的方式注册的单例 bean。
	 *
	 * @return the names of all beans defined in this factory,
	 * or an empty array if none defined
	 */
	String[] getBeanDefinitionNames();

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * 返回指定 bean 的提供者，允许实例的延迟按需检索，包括可用性和唯一性选项。
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @param allowEagerInit whether stream access may introspect <i>lazy-init singletons</i>
	 * and <i>objects created by FactoryBeans</i> - or by factory methods with a
	 * "factory-bean" reference - for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references. Only
	 * actually necessary initialization for type checking purposes will be performed;
	 * constructor and method invocations will still be avoided as far as possible.
	 * @return a corresponding provider handle
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType, boolean)
	 * @see #getBeanProvider(Class)
	 * @see #getBeansOfType(Class, boolean, boolean)
	 * @see #getBeanNamesForType(Class, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit);

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * 返回指定 bean 的提供者，允许实例的延迟按需检索，包括可用性和唯一性选项。
	 *
	 * @param requiredType type the bean must match; can be a generic type declaration.
	 * Note that collection types are not supported here, in contrast to reflective
	 * injection points. For programmatically retrieving a list of beans matching a
	 * specific type, specify the actual bean type as an argument here and subsequently
	 * use {@link ObjectProvider#orderedStream()} or its lazy streaming/iteration options.
	 * @param allowEagerInit whether stream access may introspect <i>lazy-init singletons</i>
	 * and <i>objects created by FactoryBeans</i> - or by factory methods with a
	 * "factory-bean" reference - for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references. Only
	 * actually necessary initialization for type checking purposes will be performed;
	 * constructor and method invocations will still be avoided as far as possible.
	 * @return a corresponding provider handle
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType)
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 * @see #getBeanNamesForType(ResolvableType, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit);

	/**
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值值（在 FactoryBeans 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * 会考虑由 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则原始 FactoryBean 本身会与给定类型匹配。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beanNamesForTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>This version of {@code getBeanNamesForType} matches all kinds of beans,
	 * be it singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeanNamesForType(type, true, true)}.
	 * 这个版本的 getBeanNamesForType() 会匹配所有各类的 bean，不管是单例、原型还是 FactoryBean。
	 * 在大多数实现中，返回结果同 getBeanNamesForType(type, true, true)。
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * 此方法返回的 bean 名称应尽可能按照后台配置中的定义顺序返回 bean 名称。
	 *
	 * @param type the generically typed class or interface to match
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @since 4.2
	 * @see #isTypeMatch(String, ResolvableType)
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType)
	 */
	String[] getBeanNamesForType(ResolvableType type);

	/**
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值（是 FactoryBean 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * 如果设置了 allowEagerInit，则考虑被 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象与指定类型不匹配，则原始 FactoryBean 本身将与给定类型匹配。
	 * 如果 allowEagerInit 未设置，仅检查原始 FactoryBean（FactoryBean 不需要初始化）。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beanNamesForTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * 此方法返回的 bean 名称应尽可能按照后台配置中的定义顺序返回 bean 名称。
	 *
	 * @param type the generically typed class or interface to match
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to introspect <i>lazy-init singletons</i>
	 * and <i>objects created by FactoryBeans</i> - or by factory methods with a
	 * "factory-bean" reference - for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references. Only
	 * actually necessary initialization for type checking purposes will be performed;
	 * constructor and method invocations will still be avoided as far as possible.
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @since 5.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType, boolean, boolean)
	 */
	String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit);

	/**
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值（是 FactoryBean 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * 会考虑由 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则原始 FactoryBean 本身会与给定类型匹配。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beanNamesForTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>This version of {@code getBeanNamesForType} matches all kinds of beans,
	 * be it singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeanNamesForType(type, true, true)}.
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * 此方法返回的 bean 名称应尽可能按照后台配置中的定义顺序返回 bean 名称。
	 *
	 * @param type the class or interface to match, or {@code null} for all bean names
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type);

	/**
	 * Return the names of beans matching the given type (including subclasses),
	 * judging from either bean definitions or the value of {@code getObjectType}
	 * in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值（是 FactoryBean 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * 如果设置了 allowEagerInit，则考虑被 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象与指定类型不匹配，则原始 FactoryBean 本身将与给定类型匹配。
	 * 如果 allowEagerInit 未设置，仅检查原始 FactoryBean（FactoryBean 不需要初始化）。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beanNamesForTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>Bean names returned by this method should always return bean names <i>in the
	 * order of definition</i> in the backend configuration, as far as possible.
	 * 此方法返回的 bean 名称应尽可能按照后台配置中的定义顺序返回 bean 名称。
	 *
	 * @param type the class or interface to match, or {@code null} for all bean names
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to introspect <i>lazy-init singletons</i>
	 * and <i>objects created by FactoryBeans</i> - or by factory methods with a
	 * "factory-bean" reference - for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references. Only
	 * actually necessary initialization for type checking purposes will be performed;
	 * constructor and method invocations will still be avoided as far as possible.
	 * @return the names of beans (or objects created by FactoryBeans) matching
	 * the given object type (including subclasses), or an empty array if none
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

	/**
	 * Return the bean instances that match the given object type (including
	 * subclasses), judging from either bean definitions or the value of
	 * {@code getObjectType} in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值（是 FactoryBean 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans, which means that FactoryBeans
	 * will get initialized. If the object created by the FactoryBean doesn't match,
	 * the raw FactoryBean itself will be matched against the type.
	 * 会考虑由 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则原始 FactoryBean 本身会与给定类型匹配。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beansOfTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>This version of getBeansOfType matches all kinds of beans, be it
	 * singletons, prototypes, or FactoryBeans. In most implementations, the
	 * result will be the same as for {@code getBeansOfType(type, true, true)}.
	 * <p>The Map returned by this method should always return bean names and
	 * corresponding bean instances <i>in the order of definition</i> in the
	 * backend configuration, as far as possible.
	 * 该方法返回的 Map 应尽可能按照后台配置中的定义顺序返回 bean 名称和相应的 bean 实例。
	 *
	 * @param type the class or interface to match, or {@code null} for all concrete beans
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @since 1.1.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException;

	/**
	 * Return the bean instances that match the given object type (including
	 * subclasses), judging from either bean definitions or the value of
	 * {@code getObjectType} in the case of FactoryBeans.
	 * 返回与给定类型匹配的 bean（包括子类）的名称，从 bean 定义或 getObjectType() 的返回值（是 FactoryBean 的情况下）判断。
	 * <p><b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i>
	 * check nested beans which might match the specified type as well.
	 * 该方法仅检索顶级 bean。不会检查嵌套 bean。
	 * <p>Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set,
	 * which means that FactoryBeans will get initialized. If the object created by the
	 * FactoryBean doesn't match, the raw FactoryBean itself will be matched against the
	 * type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked
	 * (which doesn't require initialization of each FactoryBean).
	 * 如果设置了 allowEagerInit，则考虑被 FactoryBean 创建的对象，意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象与指定类型不匹配，则原始 FactoryBean 本身将与给定类型匹配。
	 * 如果 allowEagerInit 未设置，仅检查原始 FactoryBean（FactoryBean 不需要初始化）。
	 * <p>Does not consider any hierarchy this factory may participate in.
	 * Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors}
	 * to include beans in ancestor factories too.
	 * 不考虑该工厂参与的任何继承层次，使用 BeanFactoryUtils.beansOfTypeIncludingAncestors() 分包含所有祖先工厂。
	 * <p>Note: Does <i>not</i> ignore singleton beans that have been registered
	 * by other means than bean definitions.
	 * 不会忽略通过其他方式（而不是 bean 定义）注册的单例。
	 * <p>The Map returned by this method should always return bean names and
	 * corresponding bean instances <i>in the order of definition</i> in the
	 * backend configuration, as far as possible.
	 * 该方法返回的 Map 应尽可能按照后台配置中的定义顺序返回 bean 名称和相应的 bean 实例。
	 *
	 * @param type the class or interface to match, or {@code null} for all concrete beans
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to introspect <i>lazy-init singletons</i>
	 * and <i>objects created by FactoryBeans</i> - or by factory methods with a
	 * "factory-bean" reference - for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references. Only
	 * actually necessary initialization for type checking purposes will be performed;
	 * constructor and method invocations will still be avoided as far as possible.
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException;

	/**
	 * Find all names of beans which are annotated with the supplied {@link Annotation}
	 * type, without creating corresponding bean instances yet.
	 * 查找所有注解了指定注解类型的 bean 名称，无需创建相应的 bean 实例。
	 * <p>Note that this method considers objects created by FactoryBeans, which means
	 * that FactoryBeans will get initialized in order to determine their object type.
	 * 此方法考虑由 FactoryBeans 创建的对象，这意味着将初始化 FactoryBeans 以确定其对象类型。
	 *
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return the names of all matching beans
	 * @since 4.0
	 * @see #getBeansWithAnnotation(Class)
	 * @see #findAnnotationOnBean(String, Class)
	 */
	String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType);

	/**
	 * Find all beans which are annotated with the supplied {@link Annotation} type,
	 * returning a Map of bean names with corresponding bean instances.
	 * 查找所有注解了指定注解类型的 bean，返回一个 Map，键为 bean 名，值为对应的 bean 实例。
	 * <p>Note that this method considers objects created by FactoryBeans, which means
	 * that FactoryBeans will get initialized in order to determine their object type.
	 * 此方法考虑由 FactoryBeans 创建的对象，这意味着将初始化 FactoryBeans 以确定其对象类型。
	 *
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return a Map with the matching beans, containing the bean names as
	 * keys and the corresponding bean instances as values
	 * @throws BeansException if a bean could not be created
	 * @since 3.0
	 * @see #findAnnotationOnBean(String, Class)
	 * @see #findAnnotationOnBean(String, Class, boolean)
	 * @see #findAllAnnotationsOnBean(String, Class, boolean)
	 */
	Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException;

	/**
	 * Find an {@link Annotation} of {@code annotationType} on the specified bean,
	 * traversing its interfaces and superclasses if no annotation can be found on
	 * the given class itself, as well as checking the bean's factory method (if any).
	 * 查找 bean 上的注解，如果在给定类本身上找不到注解，则遍历其接口和超类，并检查 bean 的工厂方法（如果有的话）。
	 *
	 * @param beanName the name of the bean to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @return the annotation of the given type if found, or {@code null} otherwise
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 3.0
	 * @see #findAnnotationOnBean(String, Class, boolean)
	 * @see #findAllAnnotationsOnBean(String, Class, boolean)
	 * @see #getBeanNamesForAnnotation(Class)
	 * @see #getBeansWithAnnotation(Class)
	 * @see #getType(String)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException;

	/**
	 * Find an {@link Annotation} of {@code annotationType} on the specified bean,
	 * traversing its interfaces and superclasses if no annotation can be found on
	 * the given class itself, as well as checking the bean's factory method (if any).
	 * 查找 bean 上的注解，如果在给定类本身上找不到注解，则遍历其接口和超类，并检查 bean 的工厂方法（如果有的话）。
	 *
	 * @param beanName the name of the bean to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return the annotation of the given type if found, or {@code null} otherwise
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.3.14
	 * @see #findAnnotationOnBean(String, Class)
	 * @see #findAllAnnotationsOnBean(String, Class, boolean)
	 * @see #getBeanNamesForAnnotation(Class)
	 * @see #getBeansWithAnnotation(Class)
	 * @see #getType(String, boolean)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException;

	/**
	 * Find all {@link Annotation} instances of {@code annotationType} on the specified
	 * bean, traversing its interfaces and superclasses if no annotation can be found on
	 * the given class itself, as well as checking the bean's factory method (if any).
	 * 查找指定 bean 上的所有指定类型的注解实例，如果在给定类本身上找不到注解，则遍历其接口和超类，并检查 bean 的工厂方法（如果有）。
	 *
	 * @param beanName the name of the bean to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * (at class, interface or factory method level of the specified bean)
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return the set of annotations of the given type found (potentially empty)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 6.0
	 * @see #getBeanNamesForAnnotation(Class)
	 * @see #findAnnotationOnBean(String, Class, boolean)
	 * @see #getType(String, boolean)
	 */
	<A extends Annotation> Set<A> findAllAnnotationsOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException;

}
