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

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * A BeanDefinition describes a bean instance, which has property values,
 * constructor argument values, and further information supplied by
 * concrete implementations.
 * 一个 BeanDefinition 对象描述了一个 bean 实例，其拥有属性值、构造方法参数值、及具体实现类提供的更多信息。
 *
 * <p>This is just a minimal interface: The main intention is to allow a
 * {@link BeanFactoryPostProcessor} to introspect and modify property values
 * and other bean metadata.
 * 这只是一个最小的接口：主要目的是允许 BeanFactoryPostProcessor 来内省和修改属性值和其他 bean 元数据。
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see ConfigurableListableBeanFactory#getBeanDefinition
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 * @since 19.03.2004
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * 标准单例作用域标识符：{@value}。
	 * <p>Note that extended bean factories might support further scopes.
	 * 注意：扩展 bean 工厂可能支持更多的作用域。
	 *
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON
	 */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * 标准原型作用域标识符：{@value}。
	 * <p>Note that extended bean factories might support further scopes.
	 * 注意：扩展 bean 工厂可能支持更多的作用域。
	 *
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE
	 */
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;


	/**
	 * Role hint indicating that a {@code BeanDefinition} is a major part
	 * of the application. Typically corresponds to a user-defined bean.
	 * 角色提示，表明 BeanDefinition 是应用程序的主要部分。通常对应于用户定义的 Bean。
	 */
	int ROLE_APPLICATION = 0;

	/**
	 * Role hint indicating that a {@code BeanDefinition} is a supporting
	 * part of some larger configuration, typically an outer
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * {@code SUPPORT} beans are considered important enough to be aware
	 * of when looking more closely at a particular
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition},
	 * but not when looking at the overall configuration of an application.
	 * 角色提示，表明 BeanDefinition 某些较大配置的支持部分，通常是一个外部的 ComponentDefinition。SUPPORT bean 被认为足够重要，
	 * 在需要更仔细地查看一个特定的 ComponentDefinition 时会被感知到，但在查看应用程序的整体配置时则不然。
	 */
	int ROLE_SUPPORT = 1;

	/**
	 * Role hint indicating that a {@code BeanDefinition} is providing an
	 * entirely background role and has no relevance to the end-user. This hint is
	 * used when registering beans that are completely part of the internal workings
	 * of a {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * 角色提示，表明 BeanDefinition 提供的完全是后台角色，与最终用户无关。当注册 bean 完全是 ComponentDefinition 内部工作一部分时，会使用这个提示。
	 */
	int ROLE_INFRASTRUCTURE = 2;


	// Modifiable attributes - 可修改的属性

	/**
	 * Set the name of the parent definition of this bean definition, if any.
	 * 设置此 bean 定义的父 bean 定义的名称（如果有）。
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * Return the name of the parent definition of this bean definition, if any.
	 * 返回此 bean 定义的父 bean 定义的名称（如果有）。
	 */
	@Nullable
	String getParentName();

	/**
	 * Specify the bean class name of this bean definition.
	 * 指定此 bean 定义的 bean 类名。
	 * <p>The class name can be modified during bean factory post-processing,
	 * typically replacing the original class name with a parsed variant of it.
	 * 可以在 Bean Factory 后置处理期间修改类名，通常将原始类名替换为其解析的变体。
	 *
	 * @see #setParentName
	 * @see #setFactoryBeanName
	 * @see #setFactoryMethodName
	 */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	 * Return the current bean class name of this bean definition.
	 * 返回此 bean 定义的当前 bean 类名。
	 * <p>Note that this does not have to be the actual class name used at runtime, in
	 * case of a child definition overriding/inheriting the class name from its parent.
	 * Also, this may just be the class that a factory method is called on, or it may
	 * even be empty in case of a factory bean reference that a method is called on.
	 * Hence, do <i>not</i> consider this to be the definitive bean type at runtime but
	 * rather only use it for parsing purposes at the individual bean definition level.
	 * 请注意，这不一定是在运行时使用的实际类名，以防子定义覆盖从其父类继承类名。此外，这可能只是调用工厂方法的类，
	 * 或者如果是一个调用了其方法的工厂 bean 引用，它甚至可能为空。因此，不要认为这是运行时的最终 bean 类型，而只将其用于单个 bean 定义级别的解析目的。
	 *
	 * @see #getParentName()
	 * @see #getFactoryBeanName()
	 * @see #getFactoryMethodName()
	 */
	@Nullable
	String getBeanClassName();

	/**
	 * Override the target scope of this bean, specifying a new scope name.
	 * 覆盖此 Bean 的目标作用域，指定新的作用域名称。
	 *
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	void setScope(@Nullable String scope);

	/**
	 * Return the name of the current target scope for this bean,
	 * or {@code null} if not known yet.
	 * 返回此 bean 的当前目标作用域的名称，如果尚不知道，则返回 null。
	 */
	@Nullable
	String getScope();

	/**
	 * Set whether this bean should be lazily initialized.
	 * 设置是否应延迟初始化此 bean。
	 * <p>If {@code false}, the bean will get instantiated on startup by bean
	 * factories that perform eager initialization of singletons.
	 * 如果设置为 false，则 bean 将在启动时由执行单例的饿汉式初始化的 bean 工厂实例化。
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * Return whether this bean should be lazily initialized, i.e. not
	 * eagerly instantiated on startup. Only applicable to a singleton bean.
	 * 返回此 bean 是否应延迟初始化，即不要在启动时饿汉式实例化。仅适用于单例 bean。
	 */
	boolean isLazyInit();

	/**
	 * Set the names of the beans that this bean depends on being initialized.
	 * The bean factory will guarantee that these beans get initialized first.
	 * 设置此 bean 初始化所依赖的 bean 的名称列表。bean 工厂将保证这些依赖 bean 首先被初始化。
	 * <p>Note that dependencies are normally expressed through bean properties or
	 * constructor arguments. This property should just be necessary for other kinds
	 * of dependencies like statics (*ugh*) or database preparation on startup.
	 * 注意，依赖关系通常通过 bean 属性或构造函数参数来表示。对于其他类型的依赖关系，如静态（*ugh*）或启动时的数据库准备，此属性应该是必需的。
	 */
	void setDependsOn(@Nullable String... dependsOn);

	/**
	 * Return the bean names that this bean depends on.
	 * 返回此 bean 所依赖的 bean 名称。
	 */
	@Nullable
	String[] getDependsOn();

	/**
	 * Set whether this bean is a candidate for getting autowired into some other bean.
	 * 设置此 bean 是否是自动装配到其他 bean 的候选者。
	 * <p>Note that this flag is designed to only affect type-based autowiring.
	 * It does not affect explicit references by name, which will get resolved even
	 * if the specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 * 注意，此标志被设计为仅影响基于类型的自动装配。它不会影响按名称进行的显式引用，即使指定的 bean 未标记为一个自动装配候选者，也会解析该引用。
	 * 因此，如果名称匹配，则按名称自动装配仍将注入 bean。
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * Return whether this bean is a candidate for getting autowired into some other bean.
	 * 返回此 bean 是否是自动装配到其他 bean 的候选者。
	 */
	boolean isAutowireCandidate();

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * 设置此 bean 是否是一个 primary 自动装配候选者。
	 * <p>If this value is {@code true} for exactly one bean among multiple
	 * matching candidates, it will serve as a tie-breaker.
	 * 如果对于多个匹配的候选者中的一个 bean，此值为 true，则它将胜出。
	 *
	 * @see #setFallback
	 */
	void setPrimary(boolean primary);

	/**
	 * Return whether this bean is a primary autowire candidate.
	 * 返回此 bean 是否是一个 primary 自动装配候选者。
	 */
	boolean isPrimary();

	/**
	 * set whether this bean is a fallback autowire candidate.
	 * 设置此 bean 是否是一个 fallback 自动装配候选者。
	 * <p>If this value is {@code true} for all beans but one among multiple
	 * matching candidates, the remaining bean will be selected.
	 * 如果对于除多个匹配候选项中的一个之外的所有 bean 的此值为 true，则将选择剩下的这个 bean。
	 *
	 * @see #setPrimary
	 * @since 6.2
	 */
	void setFallback(boolean fallback);

	/**
	 * Return whether this bean is a fallback autowire candidate.
	 * 返回此 bean 是否是一个 fallback 自动装配候选者。
	 *
	 * @since 6.2
	 */
	boolean isFallback();

	/**
	 * Specify the factory bean to use, if any.
	 * This is the name of the bean to call the specified factory method on.
	 * 指定要使用的工厂 bean（如果有）。会在该名称对应的 bean上调用指定的工厂方法。
	 * <p>a factory bean name is only necessary for instance-based factory methods.
	 * for static factory methods, the method will be derived from the bean class.
	 * 工厂 bean 名称仅对于基于实例的工厂方法是必需的。对于静态工厂方法，该方法将从 bean 类派生。
	 *
	 * @see #setFactoryMethodName
	 * @see #setBeanClassName
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * Return the factory bean name, if any.
	 * 返回工厂 bean 名称（如果有）。
	 * <p>This will be {@code null} for static factory methods which will
	 * be derived from the bean class instead.
	 * 对于静态工厂方法，会返回 null，它将从 bean 类派生。
	 *
	 * @see #getFactoryMethodName()
	 * @see #getBeanClassName()
	 */
	@Nullable
	String getFactoryBeanName();

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified.
	 * The method will be invoked on the specified factory bean, if any,
	 * or otherwise as a static method on the local bean class.
	 * 指定工厂方法（如果有）。此方法将使用构造参数调用，如果未指定参数，则不带参数调用。
	 * 该方法将在指定的工厂 bean（如果有）上调用，或者作为本地 bean 类上的静态方法调用。
	 *
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * Return a factory method, if any.
	 * 返回工厂方法（如果有）。
	 *
	 * @see #getFactoryBeanName()
	 * @see #getBeanClassName()
	 */
	@Nullable
	String getFactoryMethodName();

	/**
	 * Return the constructor argument values for this bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 *
	 * @return the ConstructorArgumentValues object (never {@code null})
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	 * Return if there are constructor argument values defined for this bean.
	 * 如果为此 bean 定义了构造函数参数值，则返回。
	 *
	 * @see #getConstructorArgumentValues()
	 * @since 5.0.2
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * Return the property values to be applied to a new instance of the bean.
	 * 返回要应用于此 bean 的新实例的属性值。
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * 返回的实例在 bean 工厂 后置处理期间修改（以应用这些属性值）。
	 *
	 * @return the MutablePropertyValues object (never {@code null})
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * Return if there are property values defined for this bean.
	 * 如果为此 bean 定义了属性值，则返回。
	 *
	 * @see #getPropertyValues()
	 * @since 5.0.2
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	/**
	 * Set the name of the initializer method.
	 * 设置初始值方法的名称。
	 *
	 * @since 5.1
	 */
	void setInitMethodName(@Nullable String initMethodName);

	/**
	 * Return the name of the initializer method.
	 * 返回初始值方法的名称。
	 *
	 * @since 5.1
	 */
	@Nullable
	String getInitMethodName();

	/**
	 * Set the name of the destroy method.
	 * 设置销毁方法的名称。
	 *
	 * @since 5.1
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);

	/**
	 * Return the name of the destroy method.
	 * 返回销毁方法的名称。
	 *
	 * @since 5.1
	 */
	@Nullable
	String getDestroyMethodName();

	/**
	 * Set the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * 为此 BeanDefinition 设置角色提示。角色提示为框架和工具类提供了一个特定 BeanDefinition 的角色和重要性的指示。
	 *
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 * @since 5.1
	 */
	void setRole(int role);

	/**
	 * Get the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * 返回此 BeanDefinition 的角色提示。角色提示为框架和工具类提供了一个特定 BeanDefinition 的角色和重要性的指示。
	 *
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	int getRole();

	/**
	 * Set a human-readable description of this bean definition.
	 * 为此 bean 定义设置可读描述。
	 *
	 * @since 5.1
	 */
	void setDescription(@Nullable String description);

	/**
	 * Return a human-readable description of this bean definition.
	 * 返回此 bean 定义的可读描述。
	 */
	@Nullable
	String getDescription();


	// Read-only attributes - 只读属性

	/**
	 * Return a resolvable type for this bean definition,
	 * based on the bean class or other specific metadata.
	 * 根据 bean 类型或其他特定元数据返回此 bean 定义的解析类型。
	 * <p>This is typically fully resolved on a runtime-merged bean definition
	 * but not necessarily on a configuration-time definition instance.
	 * 通常在合并 bean 定义时被完全解析，但不一定在配置 bean 定义实例时解析。
	 *
	 * @return the resolvable type (potentially {@link ResolvableType#NONE})
	 * @see ConfigurableBeanFactory#getMergedBeanDefinition
	 * @since 5.2
	 */
	ResolvableType getResolvableType();

	/**
	 * Return whether this a <b>Singleton</b>, with a single, shared instance
	 * returned on all calls.
	 * 返回这是否是一个单例 Singleton，并在所有调用中返回一个共享实例。
	 *
	 * @see #SCOPE_SINGLETON
	 */
	boolean isSingleton();

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance
	 * returned for each call.
	 * 返回这是否为原型 Prototype，并为每个调用返回一个独立实例。
	 *
	 * @see #SCOPE_PROTOTYPE
	 * @since 3.0
	 */
	boolean isPrototype();

	/**
	 * Return whether this bean is "abstract", that is, not meant to be instantiated
	 * itself but rather just serving as parent for concrete child bean definitions.
	 * 返回此 bean 是否是“抽象”的，即本身不会被实例化，而只是作为具体子 bean 定义的父项。
	 */
	boolean isAbstract();

	/**
	 * Return a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 * 返回此 bean 定义源自的资源的描述（以便在出现错误时显示上下文）。
	 */
	@Nullable
	String getResourceDescription();

	/**
	 * Return the originating BeanDefinition, or {@code null} if none.
	 * 返回原始 BeanDefinition，如果没有，则返回 null。
	 * <p>Allows for retrieving the decorated bean definition, if any.
	 * 允许检索装饰 bean 定义（如果有）。
	 * <p>Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 * 注意，该方法立即返回一个原始 BeanDefinition。调用者需要自行在原始 BeanDefinition 链上发起遍历，
	 * 以找到最终的原始 BeanDefinition。
	 */
	@Nullable
	BeanDefinition getOriginatingBeanDefinition();

}
