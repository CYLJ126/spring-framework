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

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

import java.beans.PropertyEditor;
import java.util.concurrent.Executor;

/**
 * Configuration interface to be implemented by most bean factories. Provides
 * facilities to configure a bean factory, in addition to the bean factory
 * client methods in the {@link org.springframework.beans.factory.BeanFactory}
 * interface.
 * 由大多数 bean 工厂实现的配置接口。提供了配置一个 bean 工厂的能力，以及在 BeanFactory 接口
 * 中的 bean 工厂客户端方法。
 *
 * <p>This bean factory interface is not meant to be used in normal application
 * code: Stick to {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * needs. This extended interface is just meant to allow for framework-internal
 * plug'n'play and for special access to bean factory configuration methods.
 * 这个 bean 工厂接口并不打算在普通的应用程序代码中使用：通常需要的是 BeanFactory 或 ListableBeanFactory。
 * 这个扩展接口仅用于框架内部的即插即用，以及对 bean 工厂配置方法的特殊访问。
 *
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 * @since 03.11.2003
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * 标准单例作用域标志。
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * 可以通过 registerScope() 添加自定义作用域。
	 *
	 * @see #registerScope
	 */
	String SCOPE_SINGLETON = "singleton";

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * 标准原型作用域标志。
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * 可以通过 registerScope() 添加自定义作用域。
	 *
	 * @see #registerScope
	 */
	String SCOPE_PROTOTYPE = "prototype";


	/**
	 * Set the parent of this bean factory.
	 * 设置父工厂。
	 * <p>Note that the parent cannot be changed: It should only be set outside
	 * a constructor if it isn't available at the time of factory instantiation.
	 * 父工厂不可变：只有在工厂实例化时 parent不可用时，才应在构造函数外部设置它。
	 *
	 * @param parentBeanFactory the parent BeanFactory
	 * @throws IllegalStateException if this factory is already associated with
	 *                               a parent BeanFactory
	 * @see #getParentBeanFactory()
	 */
	void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

	/**
	 * Set the class loader to use for loading bean classes.
	 * Default is the thread context class loader.
	 * 设置用于加载 bean 类型的类加载器。默认是线程上下文类加载器。
	 * <p>Note that this class loader will only apply to bean definitions
	 * that do not carry a resolved bean class yet. This is the case as of
	 * Spring 2.0 by default: Bean definitions only carry bean class names,
	 * to be resolved once the factory processes the bean definition.
	 * 请注意，此类加载器仅适用于尚未携带已解析的 bean 类的 bean 定义。默认情况下，从 Spring 2.0
	 * 开始就是这种情况： Bean 定义只带有 bean 类名，一旦工厂处理了 bean 定义，就可以解析了。
	 *
	 * @param beanClassLoader the class loader to use,
	 *                        or {@code null} to suggest the default class loader
	 */
	void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

	/**
	 * Return this factory's class loader for loading bean classes
	 * (only {@code null} if even the system ClassLoader isn't accessible).
	 * 返回此工厂的类加载器以加载 bean 类（仅系统类加载器都无法访问时才仅返回 null）。
	 *
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * Specify a temporary ClassLoader to use for type matching purposes.
	 * Default is none, simply using the standard bean ClassLoader.
	 * 指定一个临时的类加载器用于类型匹配，默认为 none，使用标准 bean 类加载器。
	 * <p>A temporary ClassLoader is usually just specified if
	 * <i>load-time weaving</i> is involved, to make sure that actual bean
	 * classes are loaded as lazily as possible. The temporary loader is
	 * then removed once the BeanFactory completes its bootstrap phase.
	 * 临时类加载器通常在 load-time weaving 被调用时被指定，以确保实际的 bean 类型尽可能
	 * 被懒加载。一旦 BeanFactory 完成了启动，就会删除临时类加载器。
	 *
	 * @since 2.5
	 */
	void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

	/**
	 * Return the temporary ClassLoader to use for type matching purposes,
	 * if any.
	 * 返回用于类型匹配的临时类加载器，如果存在的话。
	 *
	 * @since 2.5
	 */
	@Nullable
	ClassLoader getTempClassLoader();

	/**
	 * Set whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes. Default is on.
	 * 设置是否缓存 bean 元数据，如一个给定 bean 定义（以合并方式）及解析的 bean 类型。默认开启。
	 * <p>Turn this flag off to enable hot-refreshing of bean definition objects
	 * and in particular bean classes. If this flag is off, any creation of a bean
	 * instance will re-query the bean class loader for newly resolved classes.
	 * 关闭该标志以启用 Bean 定义对象（特别是 Bean 类型）的热刷新。如果此标志处于关闭状态，
	 * 则 Bean 实例的任何创建都将重新查询 Bean 类加载器以查找新解析的类。
	 */
	void setCacheBeanMetadata(boolean cacheBeanMetadata);

	/**
	 * Return whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes.
	 * 返回是否缓存 bean 元数据的标志。
	 */
	boolean isCacheBeanMetadata();

	/**
	 * Specify the resolution strategy for expressions in bean definition values.
	 * 指定在 bean 定义值中的表达式的解析策略，即设置 SpEL 表达式解析器。
	 * <p>There is no expression support active in a BeanFactory by default.
	 * An ApplicationContext will typically set a standard expression strategy
	 * here, supporting "#{...}" expressions in a Unified EL compatible style.
	 * 默认情况下在一个 BeanFactory 中是没有激活的表达式支持的。应用上下文通常在此设置一个标准的表达式策略，
	 * 在统一的 EL 兼容风格中，支持“#{...}”。
	 *
	 * @since 3.0
	 */
	void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

	/**
	 * Return the resolution strategy for expressions in bean definition values.
	 * 返回 bean 定义值中的表达式的解析策略。
	 *
	 * @since 3.0
	 */
	@Nullable
	BeanExpressionResolver getBeanExpressionResolver();

	/**
	 * Set the {@link Executor} (possibly a {@link org.springframework.core.task.TaskExecutor})
	 * for background bootstrapping.
	 * 为后台启动设置线程池（可能是一个 TaskExecutor）。
	 *
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#setBackgroundInit
	 * @since 6.2
	 */
	void setBootstrapExecutor(@Nullable Executor executor);

	/**
	 * Return the {@link Executor} (possibly a {@link org.springframework.core.task.TaskExecutor})
	 * for background bootstrapping, if any.
	 * 返回为后台启动设置的线程池。
	 *
	 * @since 6.2
	 */
	@Nullable
	Executor getBootstrapExecutor();

	/**
	 * Specify a {@link ConversionService} to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 * 设置一个 ConversionService 用于转换属性值，作为 JavaBeans PropertyEditor 的替代方法。
	 *
	 * @since 3.0
	 */
	void setConversionService(@Nullable ConversionService conversionService);

	/**
	 * Return the associated ConversionService, if any.
	 * 返回关联的 ConversionService。
	 *
	 * @since 3.0
	 */
	@Nullable
	ConversionService getConversionService();

	/**
	 * Add a PropertyEditorRegistrar to be applied to all bean creation processes.
	 * 为 bean 的创建过程，添加一个 PropertyEditorRegistrar 注册器。
	 * <p>Such a registrar creates new PropertyEditor instances and registers them
	 * on the given registry, fresh for each bean creation attempt. This avoids
	 * the need for synchronization on custom editors; hence, it is generally
	 * preferable to use this method instead of {@link #registerCustomEditor}.
	 * 这个注册器创建一个新的 PropertyEditor 实例，并注册到指定注册表中，为每个 bean 的创建作刷新。
	 * 这避免了要在自定义编辑器中进行同步，因此，它通常比使用 registerCustomEditor() 要更好。
	 *
	 * @param registrar the PropertyEditorRegistrar to register
	 */
	void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

	/**
	 * Register the given custom property editor for all properties of the
	 * given type. To be invoked during factory configuration.
	 * 为给定类型的所有属性注册自定义属性编辑器。在工厂配置中被调用。
	 * <p>Note that this method will register a shared custom editor instance;
	 * access to that instance will be synchronized for thread-safety. It is
	 * generally preferable to use {@link #addPropertyEditorRegistrar} instead
	 * of this method, to avoid for the need for synchronization on custom editors.
	 * 该方法将注册一个共享的自定义编辑器实例，对该实例的访问将会被同步，以确保线程安全性。通常更好的方式
	 * 是使用 addPropertyEditorRegistrar() 来替代当前方法，以避免在自定义编辑器中同步。
	 *
	 * @param requiredType        type of the property
	 * @param propertyEditorClass the {@link PropertyEditor} class to register
	 */
	void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * 使用已在 BeanFactory 中注册的自定义编辑器来初始化给定的 PropertyEditorRegistry。
	 *
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

	/**
	 * Set a custom type converter that this BeanFactory should use for converting
	 * bean property values, constructor argument values, etc.
	 * 设置一个 BeanFactory 用于转换 bean 属性值、构造器参数值竺的自定义类型转换器。
	 * <p>This will override the default PropertyEditor mechanism and hence make
	 * any custom editors or custom editor registrars irrelevant.
	 * 这会重写默认的 PropertyEditor 机制，从而使任何自定义编辑器或自定义编辑器注册表变得无关紧要。
	 *
	 * @see #addPropertyEditorRegistrar
	 * @see #registerCustomEditor
	 * @since 2.5
	 */
	void setTypeConverter(TypeConverter typeConverter);

	/**
	 * Obtain a type converter as used by this BeanFactory. This may be a fresh
	 * instance for each call, since TypeConverters are usually <i>not</i> thread-safe.
	 * 返回该 BeanFactory 使用的类型转换器。由于 TypeConverters 通常不是线程安全的，可能每次调用都是一个新的实例。
	 * <p>If the default PropertyEditor mechanism is active, the returned
	 * TypeConverter will be aware of all custom editors that have been registered.
	 * 如果默认的 PropertyEditor 机制处于活动状态，返回的 TypeConverter 将知道已注册的所有自定义编辑器。
	 *
	 * @since 2.5
	 */
	TypeConverter getTypeConverter();

	/**
	 * Add a String resolver for embedded values such as annotation attributes.
	 * 为嵌入式值（如注解属性）添加一个字符串解析器。
	 *
	 * @param valueResolver the String resolver to apply to embedded values
	 * @since 3.0
	 */
	void addEmbeddedValueResolver(StringValueResolver valueResolver);

	/**
	 * Determine whether an embedded value resolver has been registered with this
	 * bean factory, to be applied through {@link #resolveEmbeddedValue(String)}.
	 * 判断是否注册了一个嵌入式值字符串解析器。
	 *
	 * @since 4.3
	 */
	boolean hasEmbeddedValueResolver();

	/**
	 * Resolve the given embedded value, for example, an annotation attribute.
	 * 解析给定的嵌入式值，如注解属性。
	 *
	 * @param value the value to resolve
	 * @return the resolved value (may be the original value as-is)
	 * @since 3.0
	 */
	@Nullable
	String resolveEmbeddedValue(String value);

	/**
	 * Add a new BeanPostProcessor that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * 添加一个新的 BeanPostProcessor 用于该工厂的 bean 创建。在工厂配置时被调用。
	 * <p>Note: Post-processors submitted here will be applied in the order of
	 * registration; any ordering semantics expressed through implementing the
	 * {@link org.springframework.core.Ordered} interface will be ignored. Note
	 * that autodetected post-processors (for example, as beans in an ApplicationContext)
	 * will always be applied after programmatically registered ones.
	 * 此处提交的后置处理器将按注册顺序应用，通过实现 Ordered 接口表示的任何排序语义都将被忽略。
	 * 自动检查到的后置处理器（如作为 ApplicationContext 中的 bean）总是在代码注册的后置处理器之后被应用。
	 *
	 * @param beanPostProcessor the post-processor to register
	 */
	void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

	/**
	 * Return the current number of registered BeanPostProcessors, if any.
	 * 返回当前 BeanPostProcessor 的数量，如果有的话。
	 */
	int getBeanPostProcessorCount();

	/**
	 * Register the given scope, backed by the given Scope implementation.
	 * 注册给定的作用域，由给定的 Scope 实现提供支持。
	 *
	 * @param scopeName the scope identifier
	 * @param scope     the backing Scope implementation
	 */
	void registerScope(String scopeName, Scope scope);

	/**
	 * Return the names of all currently registered scopes.
	 * 返回当前注册的作用域的名字数组。
	 * <p>This will only return the names of explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * 只会返回显式注册的作用域名。内建的如"singleton"和"prototype"将不会暴露出去。
	 *
	 * @return the array of scope names, or an empty array if none
	 * @see #registerScope
	 */
	String[] getRegisteredScopeNames();

	/**
	 * Return the Scope implementation for the given scope name, if any.
	 * 返回给定名字的 Scope 作用域实现。
	 * <p>This will only return explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * 只会返回显式注册的作用域。内建的如"singleton"和"prototype"将不会暴露出去。
	 *
	 * @param scopeName the name of the scope
	 * @return the registered Scope implementation, or {@code null} if none
	 * @see #registerScope
	 */
	@Nullable
	Scope getRegisteredScope(String scopeName);

	/**
	 * Set the {@code ApplicationStartup} for this bean factory.
	 * 为此 bean 工厂设置 ApplicationStartup 启动步骤。
	 * <p>This allows the application context to record metrics during application startup.
	 * 这允许应用程序上下文在应用程序启动期间记录指标
	 *
	 * @param applicationStartup the new application startup
	 * @since 5.3
	 */
	void setApplicationStartup(ApplicationStartup applicationStartup);

	/**
	 * Return the {@code ApplicationStartup} for this bean factory.
	 * 返回 bean 工厂的 ApplicationStartup。
	 *
	 * @since 5.3
	 */
	ApplicationStartup getApplicationStartup();

	/**
	 * Copy all relevant configuration from the given other factory.
	 * 从给定的其他工厂中复制所有相关配置。
	 * <p>Should include all standard configuration settings as well as
	 * BeanPostProcessors, Scopes, and factory-specific internal settings.
	 * Should not include any metadata of actual bean definitions,
	 * such as BeanDefinition objects and bean name aliases.
	 * 应包括所有标准配置，及 BeanPostProcessor、Scope 和特定于工厂的内部配置。
	 * 不应包括实际的 bean 定义的元数据，如 BeanDefinition 对象、bean 别名。
	 *
	 * @param otherFactory the other BeanFactory to copy from
	 */
	void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

	/**
	 * Given a bean name, create an alias. We typically use this method to
	 * support names that are illegal within XML ids (used for bean names).
	 * 对给定 bean 名，创建一个别名。通常使用此方法来支持 XML ID 中非法的名称（用于 bean 名称）。
	 * <p>Typically invoked during factory configuration, but can also be
	 * used for runtime registration of aliases. Therefore, a factory
	 * implementation should synchronize alias access.
	 * 通常在工厂配置期间调用，但也可用于别名的运行时注册。因此，工厂的实现应该对别名的访问进行同步。
	 *
	 * @param beanName the canonical name of the target bean
	 * @param alias    the alias to be registered for the bean
	 * @throws BeanDefinitionStoreException if the alias is already in use
	 */
	void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

	/**
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * 使用给定的 StringValueResolver 解析所有别名的目标名称和已注册到该工厂的别名。
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * 该值解析器可以解析目标 bean 名称甚至别名中的占位符。
	 *
	 * @param valueResolver the StringValueResolver to apply
	 * @since 2.5
	 */
	void resolveAliases(StringValueResolver valueResolver);

	/**
	 * Return a merged BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * Considers bean definitions in ancestor factories as well.
	 * 为给定的 bean 名返回一个合并的 BeanDefinition，必要时将子 bean 定义与其父 bean 合并。
	 * 也会考虑到祖先工厂中的 bean 定义。
	 *
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) BeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @since 2.5
	 */
	BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Determine whether the bean with the given name is a FactoryBean.
	 * 判断给定名字是否是一个 FactoryBean。
	 *
	 * @param name the name of the bean to check
	 * @return whether the bean is a FactoryBean
	 * ({@code false} means the bean exists but is not a FactoryBean)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.5
	 */
	boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Explicitly control the current in-creation status of the specified bean.
	 * For container-internal use only.
	 * 显式控制指定 bean 的当前创建中状态。仅供容器内部使用。
	 *
	 * @param beanName   the name of the bean
	 * @param inCreation whether the bean is currently in creation
	 * @since 3.1
	 */
	void setCurrentlyInCreation(String beanName, boolean inCreation);

	/**
	 * Determine whether the specified bean is currently in creation.
	 * 判断指定的 Bean 当前是否正在创建中。
	 *
	 * @param beanName the name of the bean
	 * @return whether the bean is currently in creation
	 * @since 2.5
	 */
	boolean isCurrentlyInCreation(String beanName);

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * 为给定的 bean 注册一个依赖的 bean，该 bean 在依赖的 bean 之前先被销毁。
	 *
	 * @param beanName          the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 * @since 2.5
	 */
	void registerDependentBean(String beanName, String dependentBeanName);

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * 返回依赖于指定 bean 的所有 bean 的名称（如果有）。
	 *
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 * @since 2.5
	 */
	String[] getDependentBeans(String beanName);

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * 返回指定 bean 所依赖的所有 bean 的名称（如果有）。
	 *
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 * @since 2.5
	 */
	String[] getDependenciesForBean(String beanName);

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to its bean definition.
	 * 根据 bean 定义销毁给定的 bean 实例（通常是从此工厂获得的原型实例）。
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 应捕获并记录销毁过程中出现的任何异常，而不是传播给此方法的调用方。
	 *
	 * @param beanName     the name of the bean definition
	 * @param beanInstance the bean instance to destroy
	 */
	void destroyBean(String beanName, Object beanInstance);

	/**
	 * Destroy the specified scoped bean in the current target scope, if any.
	 * 销毁当前目标作用域中的指定作用域 Bean（如果有）。
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 应捕获并记录销毁过程中出现的任何异常，而不是传播给此方法的调用方。
	 *
	 * @param beanName the name of the scoped bean
	 */
	void destroyScopedBean(String beanName);

	/**
	 * Destroy all singleton beans in this factory, including inner beans that have
	 * been registered as disposable. To be called on shutdown of a factory.
	 * 销毁此工厂中的所有单例 bean，包括已注册为 disposable 的内部 bean。在工厂关闭时调用。
	 *
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 应捕获并记录销毁过程中出现的任何异常，而不是传播给此方法的调用方。
	 */
	void destroySingletons();

}
