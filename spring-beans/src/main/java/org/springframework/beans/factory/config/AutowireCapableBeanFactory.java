/*
 * Copyright 2002-2023 the original author or authors.
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
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Extension of the {@link org.springframework.beans.factory.BeanFactory}
 * interface to be implemented by bean factories that are capable of
 * autowiring, provided that they want to expose this functionality for
 * existing bean instances.
 * BeanFactory 接口的扩展，由能够自动装配的 bean 工厂实现，前提是它们希望为现有的 bean 实例公开此功能。
 *
 * <p>This subinterface of BeanFactory is not meant to be used in normal
 * application code: stick to {@link org.springframework.beans.factory.BeanFactory}
 * or {@link org.springframework.beans.factory.ListableBeanFactory} for
 * typical use cases.
 * 该 BeanFactory 的子接口并不打算在普通应用代码中使用：对于典型用例，请坚持使用 BeanFactory 或 ListableBeanFactory。
 *
 * <p>Integration code for other frameworks can leverage this interface to
 * wire and populate existing bean instances that Spring does not control
 * the lifecycle of. This is particularly useful for WebWork Actions and
 * Tapestry Page objects, for example.
 * 其他框架的集成代码可以利用此接口来连接和填充那些 Spring 不控制其生命周期的现有 bean 实例。
 * 例如，这对于 WebWork Action 和 Tapestry Page 对象特别有用。
 *
 *
 * <p>Note that this interface is not implemented by
 * {@link org.springframework.context.ApplicationContext} facades,
 * as it is hardly ever used by application code. That said, it is available
 * from an application context too, accessible through ApplicationContext's
 * {@link org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()}
 * method.
 * 注意，这个接口不是由 org.springframework.context.ApplicationContext 门面实现的，因为它几乎从未被应用程序代码使用过。
 * 也就是说，它也可以从应用程序上下文中获得，通过 ApplicationContext 的 getAutowireCapableBeanFactory() 方法访问。
 *
 * <p>You may also implement the {@link org.springframework.beans.factory.BeanFactoryAware}
 * interface, which exposes the internal BeanFactory even when running in an
 * ApplicationContext, to get access to an AutowireCapableBeanFactory:
 * simply cast the passed-in BeanFactory to AutowireCapableBeanFactory.
 * 也可以实现 BeanFactoryAware 接口，即使在 ApplicationContext 中运行时，它也暴露了内部的 BeanFactory，
 * 以获得对 AutowireCapableBeanFactory 的访问：只需将传入的 BeanFactory 强制转换为 AutowireCapableBeanFactory。
 *
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
 * @since 04.12.2003
 */
public interface AutowireCapableBeanFactory extends BeanFactory {

	/**
	 * Constant that indicates no externally defined autowiring. Note that
	 * BeanFactoryAware etc and annotation-driven injection will still be applied.
	 * 表示没有外部定义的自动装配。请注意，BeanFactoryAware 等和注解驱动的注入仍然会被应用。
	 *
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_NO = 0;

	/**
	 * Constant that indicates autowiring bean properties by name
	 * (applying to all bean property setters).
	 * 指示按名称自动装配 Bean 属性（适用于所有 Bean 属性的 setter 方法）。
	 *
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_BY_NAME = 1;

	/**
	 * Constant that indicates autowiring bean properties by type
	 * (applying to all bean property setters).
	 * 指示按类型自动装配 Bean 属性（适用于所有 Bean 属性的 setter 方法）。
	 *
	 * @see #autowire
	 * @see #autowireBeanProperties
	 */
	int AUTOWIRE_BY_TYPE = 2;

	/**
	 * Constant that indicates autowiring the greediest constructor that
	 * can be satisfied (involves resolving the appropriate constructor).
	 * 表示自动装配可以满足的最贪婪的构造函数（涉及解析适当的构造函数）。
	 *
	 * @see #autowire
	 */
	int AUTOWIRE_CONSTRUCTOR = 3;

	/**
	 * Constant that indicates determining an appropriate autowire strategy
	 * through introspection of the bean class.
	 * 表示通过内省 Bean 类来确定适合的自动装配策略。
	 *
	 * @see #autowire
	 * @deprecated as of Spring 3.0: If you are using mixed autowiring strategies,
	 * prefer annotation-based autowiring for clearer demarcation of autowiring needs.
	 * Spring 3.0 开始被标注为 Deprecated：如果使用混合自动装配策略，首选基于注释的自动装配，以便更清楚地划分自动装配需求。
	 */
	@Deprecated
	int AUTOWIRE_AUTODETECT = 4;

	/**
	 * Suffix for the "original instance" convention when initializing an existing
	 * bean instance: to be appended to the fully-qualified bean class name,
	 * for example, "com.mypackage.MyClass.ORIGINAL", in order to enforce the given instance
	 * to be returned, i.e. no proxies etc.
	 * 初始化现有 bean 实例时对 “original instance” 约定的后缀：追加到全限定 bean 类名上，
	 * 如“com.mypackage.MyClass.ORIGINAL”，以便强制返回给定的实例，即没有代理等。
	 *
	 * @see #initializeBean(Object, String)
	 * @see #applyBeanPostProcessorsBeforeInitialization(Object, String)
	 * @see #applyBeanPostProcessorsAfterInitialization(Object, String)
	 * @since 5.1
	 */
	String ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL";


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances - 创建和填充外部 Bean 实例的典型方法
	//-------------------------------------------------------------------------

	/**
	 * Fully create a new bean instance of the given class.
	 * 对给定类型，完整地创建一个新的 bean 实例。
	 * <p>Performs full initialization of the bean, including all applicable
	 * {@link BeanPostProcessor BeanPostProcessors}.
	 * 执行 bean 的完全初始化，包括所有可用的 BeanPostProcessor。
	 * <p>Note: This is intended for creating a fresh instance, populating annotated
	 * fields and methods as well as applying all standard bean initialization callbacks.
	 * Constructor resolution is based on Kotlin primary / single public / single non-public,
	 * with a fallback to the default constructor in ambiguous scenarios, also influenced
	 * by {@link SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors}
	 * (for example, for annotation-driven constructor selection).
	 * 这主要用于创建一个新实例，填充注解字段和方法，及应用所有标准 bean 初始化回调方法。
	 * 构造器解析基于 Kotlin primary / single public / single non-public，模棱两可的情况下会回退到默认构造函数，
	 * 也受 {@link SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors} 的影响（例如，用于注解驱动的构造函数选择）。
	 *
	 * @param beanClass the class of the bean to create
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 */
	<T> T createBean(Class<T> beanClass) throws BeansException;

	/**
	 * Populate the given bean instance through applying after-instantiation callbacks
	 * and bean property post-processing (for example, for annotation-driven injection).
	 * <p>Note: This is essentially intended for (re-)populating annotated fields and
	 * methods, either for new instances or for deserialized instances. It does
	 * <i>not</i> imply traditional by-name or by-type autowiring of properties;
	 * use {@link #autowireBeanProperties} for those purposes.
	 * 应用初始化后回调方法和 bean 属性后置处理来填充给定的 bean 实例（如注解驱动注入）。
	 * 注意：这本质上是用于（重新）填充带注解的字段和方法，无论是新实例还是反序列化实例。
	 * 它并不意味着传统的按名称或按类型自动装配属性，如果是这样，请使用 autowireBeanProperties()。
	 *
	 * @param existingBean the existing bean instance
	 * @throws BeansException if wiring failed
	 */
	void autowireBean(Object existingBean) throws BeansException;

	/**
	 * Configure the given raw bean: autowiring bean properties, applying
	 * bean property values, applying factory callbacks such as {@code setBeanName}
	 * and {@code setBeanFactory}, and also applying all bean post processors
	 * (including ones which might wrap the given raw bean).
	 * 配置一个给定的原始 bean：自动装配 bean 属性，应用 bean 属性值，应用工厂回调函数，如 setBeanName()
	 * 和 setBeanFactory()，也应用所有 Bean 后置处理器（包括那些可能包装了给定原始 Bean 的处理器）。
	 * <p>This is effectively a superset of what {@link #initializeBean} provides,
	 * fully applying the configuration specified by the corresponding bean definition.
	 * <b>Note: This method requires a bean definition for the given name!</b>
	 * 这实际上是 initializeBean() 提供的超集，完全应用了由相应的 bean 定义指定的配置。
	 * 注意：此方法需要给定名称的 bean 定义！
	 *
	 * @param existingBean the existing bean instance
	 * @param beanName     the name of the bean, to be passed to it if necessary
	 *                     (a bean definition of that name has to be available)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @throws BeansException                                                  if the initialization failed
	 * @see #initializeBean
	 */
	Object configureBean(Object existingBean, String beanName) throws BeansException;


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle - 对 Bean 生命周期进行精细控制的专用方法
	//-------------------------------------------------------------------------

	/**
	 * Fully create a new bean instance of the given class with the specified
	 * autowire strategy. All constants defined in this interface are supported here.
	 * 根据给定的自动装配策略，对给定类型完全地创建一个新的 bean 实例。该接口中定义的所有常量在此均支持。
	 * <p>Performs full initialization of the bean, including all applicable
	 * {@link BeanPostProcessor BeanPostProcessors}. This is effectively a superset
	 * of what {@link #autowire} provides, adding {@link #initializeBean} behavior.
	 * 执行 bean 的完全初始化，包括所有可用的 BeanPostProcessor。这是 autowire() 的超集，增加了 initializeBean() 行为。
	 *
	 * @param beanClass       the class of the bean to create
	 * @param autowireMode    by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for objects
	 *                        (not applicable to autowiring a constructor, thus ignored there)
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @deprecated as of 6.1, in favor of {@link #createBean(Class)}
	 */
	@Deprecated(since = "6.1")
	Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;

	/**
	 * Instantiate a new bean instance of the given class with the specified autowire
	 * strategy. All constants defined in this interface are supported here.
	 * Can also be invoked with {@code AUTOWIRE_NO} in order to just apply
	 * before-instantiation callbacks (for example, for annotation-driven injection).
	 * 根据给定的自动装配策略，对给定类型创建一个新的 bean 实例。该接口中定义的所有常量在此均支持。
	 * 也可以使用 AUTOWIRE_NO 来调用，以便仅应用实例化后回调（如注解驱动注入）。
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the construction of the instance.
	 * 不会应用标准的 BeanPostProcessor 回调方法，也不执行 bean 的任何更进一步的初始化。这个接口提供不同的细粒度操作，
	 * 如 initializeBean()。但是，如果适用于实例的构造，则会应用 InstantiationAwareBeanPostProcessor 回调方法。
	 *
	 * @param beanClass       the class of the bean to instantiate
	 * @param autowireMode    by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for object
	 *                        references in the bean instance (not applicable to autowiring a constructor,
	 *                        thus ignored there)
	 * @return the new bean instance
	 * @throws BeansException if instantiation or wiring failed
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_AUTODETECT
	 * @see #initializeBean
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException;

	/**
	 * Autowire the bean properties of the given bean instance by name or type.
	 * Can also be invoked with {@code AUTOWIRE_NO} in order to just apply
	 * after-instantiation callbacks (for example, for annotation-driven injection).
	 * 对给定 bean 实例通过按名称或按类型来装配其属性。也可以使用 AUTOWIRE_NO 来调用，以便仅应用实例化后回调（如注解驱动注入）。
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the configuration of the instance.
	 * 不会应用标准的 BeanPostProcessor 回调方法，也不执行 bean 的任何更进一步的初始化。这个接口提供不同的细粒度操作，
	 * 如 initializeBean()。但是，如果适用于实例的构造，则会应用 InstantiationAwareBeanPostProcessor 回调方法。
	 *
	 * @param existingBean    the existing bean instance
	 * @param autowireMode    by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for object
	 *                        references in the bean instance
	 * @throws BeansException if wiring failed
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_NO
	 */
	void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException;

	/**
	 * Apply the property values of the bean definition with the given name to
	 * the given bean instance. The bean definition can either define a fully
	 * self-contained bean, reusing its property values, or just property values
	 * meant to be used for existing bean instances.
	 * 将给定名字对应的 bean 定义中的属性值应用到给定的 bean 实例上。bean 定义可以定义一个完全独立的 Bean，
	 * 重用其属性值，或者只定义用于现有 Bean 实例的属性值。
	 * <p>This method does <i>not</i> autowire bean properties; it just applies
	 * explicitly defined property values. Use the {@link #autowireBeanProperties}
	 * method to autowire an existing bean instance.
	 * 这个方法不会自动装配 bean 属性，它只应用显式定义的属性值。可以使用 autowireBeanProperties() 自动装配一个已存在的 bean 实例。
	 * <b>Note: This method requires a bean definition for the given name!</b>
	 * 注意：此方法需要给定名称的 bean 定义！
	 * <p>Does <i>not</i> apply standard {@link BeanPostProcessor BeanPostProcessors}
	 * callbacks or perform any further initialization of the bean. This interface
	 * offers distinct, fine-grained operations for those purposes, for example
	 * {@link #initializeBean}. However, {@link InstantiationAwareBeanPostProcessor}
	 * callbacks are applied, if applicable to the configuration of the instance.
	 * 不会应用标准的 BeanPostProcessor 回调方法，也不执行 bean 的任何更进一步的初始化。这个接口提供不同的细粒度操作，
	 * 如 initializeBean()。但是，如果适用于实例的构造，则会应用 InstantiationAwareBeanPostProcessor 回调方法。
	 *
	 * @param existingBean the existing bean instance
	 * @param beanName     the name of the bean definition in the bean factory
	 *                     (a bean definition of that name has to be available)
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @throws BeansException                                                  if applying the property values failed
	 * @see #autowireBeanProperties
	 */
	void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException;

	/**
	 * Initialize the given raw bean, applying factory callbacks
	 * such as {@code setBeanName} and {@code setBeanFactory},
	 * also applying all bean post processors (including ones which
	 * might wrap the given raw bean).
	 * 初始化给定的原始 bean，应用诸如 setBeanName()、setBeanFactory() 等工厂回调，
	 * 也应用所有 Bean 后置处理器（包括那些可能包装了给定原始 Bean 的处理器）。
	 * <p>Note that no bean definition of the given name has to exist
	 * in the bean factory. The passed-in bean name will simply be used
	 * for callbacks but not checked against the registered bean definitions.
	 * Bean 工厂中不必存在给定名称的 bean 定义。传入的 bean 名称将仅用于回调，而不会根据已注册的 bean 定义进行检查。
	 *
	 * @param existingBean the existing bean instance
	 * @param beanName     the name of the bean, to be passed to it if necessary
	 *                     (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 *                     can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 *                     enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if the initialization failed
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 */
	Object initializeBean(Object existingBean, String beanName) throws BeansException;

	/**
	 * Apply {@link BeanPostProcessor BeanPostProcessors} to the given existing bean
	 * instance, invoking their {@code postProcessBeforeInitialization} methods.
	 * The returned bean instance may be a wrapper around the original.
	 * 对给定、存在的 bean 实例应用 BeanPostProcessor，调用其 postProcessBeforeInitialization() 方法。
	 * 返回的 bean 实例可能是一个原始对象的一个包装对象。
	 *
	 * @param existingBean the existing bean instance
	 * @param beanName     the name of the bean, to be passed to it if necessary
	 *                     (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 *                     can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 *                     enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if any post-processing failed
	 * @see BeanPostProcessor#postProcessBeforeInitialization
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 * @deprecated as of 6.1, in favor of implicit post-processing through
	 * {@link #initializeBean(Object, String)}
	 * 从 6.1 开始，支持通过 initializeBean(Object, String) 进行隐式的后置处理。
	 */
	@Deprecated(since = "6.1")
	Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException;

	/**
	 * Apply {@link BeanPostProcessor BeanPostProcessors} to the given existing bean
	 * instance, invoking their {@code postProcessAfterInitialization} methods.
	 * The returned bean instance may be a wrapper around the original.
	 * 对给定、存在的 bean 实例应用 BeanPostProcessor，调用其 postProcessAfterInitialization() 方法。
	 * 返回的 bean 实例可能是一个原始对象的一个包装对象。
	 *
	 * @param existingBean the existing bean instance
	 * @param beanName     the name of the bean, to be passed to it if necessary
	 *                     (only passed to {@link BeanPostProcessor BeanPostProcessors};
	 *                     can follow the {@link #ORIGINAL_INSTANCE_SUFFIX} convention in order to
	 *                     enforce the given instance to be returned, i.e. no proxies etc)
	 * @return the bean instance to use, either the original or a wrapped one
	 * @throws BeansException if any post-processing failed
	 * @see BeanPostProcessor#postProcessAfterInitialization
	 * @see #ORIGINAL_INSTANCE_SUFFIX
	 * @deprecated as of 6.1, in favor of implicit post-processing through
	 * {@link #initializeBean(Object, String)}
	 * 从 6.1 开始，支持通过 initializeBean(Object, String) 进行隐式的后置处理。
	 */
	@Deprecated(since = "6.1")
	Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException;

	/**
	 * Destroy the given bean instance (typically coming from {@link #createBean(Class)}),
	 * applying the {@link org.springframework.beans.factory.DisposableBean} contract as well as
	 * registered {@link DestructionAwareBeanPostProcessor DestructionAwareBeanPostProcessors}.
	 * 销毁 bean 实例（通常来自于 createBean(Class)），对其应用 DisposableBean 协议及注册的 DestructionAwareBeanPostProcessor。
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 应捕获并记录销毁过程中出现的任何异常，而不是传播给此方法的调用方。
	 *
	 * @param existingBean the bean instance to destroy
	 */
	void destroyBean(Object existingBean);


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points - 用于解析注入点的委托方法
	//-------------------------------------------------------------------------

	/**
	 * Resolve the bean instance that uniquely matches the given object type, if any,
	 * including its bean name.
	 * 解析与给定对象类型（如果有）唯一匹配的 Bean 实例，包括其 Bean 名称，并返回对应的 NamedBeanHolder 包装实例。
	 * <p>This is effectively a variant of {@link #getBean(Class)} which preserves the
	 * bean name of the matching instance.
	 * 这是 getBean(Class) 的一个变体方法，保留了该匹配实例的 bean 名称。
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return the bean name plus bean instance
	 * @throws NoSuchBeanDefinitionException   if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException                  if the bean could not be created
	 * @see #getBean(Class)
	 * @since 4.3.3
	 */
	<T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException;

	/**
	 * Resolve a bean instance for the given bean name, providing a dependency descriptor
	 * for exposure to target factory methods.
	 * 对给定 bean 名解析 bean 实例，提供了暴露给目标工厂方法的依赖描述对象。
	 * <p>This is effectively a variant of {@link #getBean(String, Class)} which supports
	 * factory methods with an {@link org.springframework.beans.factory.InjectionPoint}
	 * argument.
	 * 这是 getBean(String, Class) 的一个变体方法，支持带 InjectionPoint 参数的工厂方法。
	 *
	 * @param name       the name of the bean to look up
	 * @param descriptor the dependency descriptor for the requesting injection point
	 * @return the corresponding bean instance
	 * @throws NoSuchBeanDefinitionException if there is no bean with the specified name
	 * @throws BeansException                if the bean could not be created
	 * @see #getBean(String, Class)
	 * @since 5.1.5
	 */
	Object resolveBeanByName(String name, DependencyDescriptor descriptor) throws BeansException;

	/**
	 * Resolve the specified dependency against the beans defined in this factory.
	 * 对此工厂中定义的 bean 解析指定的依赖项。
	 *
	 * @param descriptor         the descriptor for the dependency (field/method/constructor)
	 * @param requestingBeanName the name of the bean which declares the given dependency
	 * @return the resolved object, or {@code null} if none found
	 * @throws NoSuchBeanDefinitionException   if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException                  if dependency resolution failed for any other reason
	 * @see #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)
	 * @since 2.5
	 */
	@Nullable
	Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException;

	/**
	 * Resolve the specified dependency against the beans defined in this factory.
	 * 对此工厂中定义的 bean 解析指定的依赖项。
	 *
	 * @param descriptor         the descriptor for the dependency (field/method/constructor)
	 * @param requestingBeanName the name of the bean which declares the given dependency
	 * @param autowiredBeanNames a Set that all names of autowired beans (used for
	 *                           resolving the given dependency) are supposed to be added to
	 * @param typeConverter      the TypeConverter to use for populating arrays and collections
	 * @return the resolved object, or {@code null} if none found
	 * @throws NoSuchBeanDefinitionException   if no matching bean was found
	 * @throws NoUniqueBeanDefinitionException if more than one matching bean was found
	 * @throws BeansException                  if dependency resolution failed for any other reason
	 * @see DependencyDescriptor
	 * @since 2.5
	 */
	@Nullable
	Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
							 @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException;

}
