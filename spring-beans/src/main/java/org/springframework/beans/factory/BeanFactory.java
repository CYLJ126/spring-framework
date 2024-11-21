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

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * The root interface for accessing a Spring bean container.
 * 访问一个 Spring bean 容器的根接口。
 *
 * <p>This is the basic client view of a bean container;
 * further interfaces such as {@link ListableBeanFactory} and
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * are available for specific purposes.
 * 这是一个 bean 容器的基础客户端视图，其他接口如 ListableBeanFactory 和
 * ConfigurableBeanFactory 则有其他用处。
 *
 * <p>This interface is implemented by objects that hold a number of bean definitions,
 * each uniquely identified by a String name. Depending on the bean definition,
 * the factory will return either an independent instance of a contained object
 * (the Prototype design pattern), or a single shared instance (a superior
 * alternative to the Singleton design pattern, in which the instance is a
 * singleton in the scope of the factory). Which type of instance will be returned
 * depends on the bean factory configuration: the API is the same. Since Spring
 * 2.0, further scopes are available depending on the concrete application
 * context (for example, "request" and "session" scopes in a web environment).
 * 这个接口被持有一系列 bean 定义的对象所实现，每个都通过一个字符串名字唯一标识。根据 bean 定义，
 * 该工厂要么返回一个容器对象的独立实例（原型模式），或一个共享单例（单例模式的更优方案，在工厂作用域内是单例。）
 * 根据 bean 工厂配置，返回对应的实例类型：API 是一样的。从 Spring 2.0 开始，在具体的应用上下文中，
 * 可以使用更多的作用域（如 web 环境中的“request”和“session”作用域）。
 *
 *
 * <p>The point of this approach is that the BeanFactory is a central registry
 * of application components, and centralizes configuration of application
 * components (no more do individual objects need to read properties files,
 * for example). See chapters 4 and 11 of "Expert One-on-One J2EE Design and
 * Development" for a discussion of the benefits of this approach.
 * 这种方式的关键点在于，BeanFactory 是应用组件的核心注册表，集中了应用组件的配置（如
 * 单个对象不再需要读取属性文件）。参考《Expert One-on-One J2EE Design and Development》
 * 的第4、11章，关于这个方法的优点讨论。
 *
 * <p>Note that it is generally better to rely on Dependency Injection
 * ("push" configuration) to configure application objects through setters
 * or constructors, rather than use any form of "pull" configuration like a
 * BeanFactory lookup. Spring's Dependency Injection functionality is
 * implemented using this BeanFactory interface and its subinterfaces.
 * 依靠依赖注入（“推”配置）通常更好一些，通过 setter 方法或构造器来配置应用对象，而不是使用
 * 任何形式的“拉”配置，如 BeanFactory 查找。Spring 的依赖注入功能通过当前这个 BeanFactory
 * 接口及其子接口来实现。
 *
 * <p>Normally a BeanFactory will load bean definitions stored in a configuration
 * source (such as an XML document), and use the {@code org.springframework.beans}
 * package to configure the beans. However, an implementation could simply return
 * Java objects it creates as necessary directly in Java code. There are no
 * constraints on how the definitions could be stored: LDAP, RDBMS, XML,
 * properties file, etc. Implementations are encouraged to support references
 * amongst beans (Dependency Injection).
 * 正常情况下，一个 BeanFactory 会加载存储在配置资源（如一个 XML 文档）中的 bean 定义，并使用
 * org.springframework.beans 包来配置这些 bean。然而，一个实现会根据需要在 Java 代码中直接
 * 返回一个 Java 对象。至于这些定义怎么存储，是没有任何约束的：LDAP，RDBMS，XML，属性文件等都可以。
 * 鼓励该接口的实现支持 bean 间引用（依赖注入）。
 *
 * <p>In contrast to the methods in {@link ListableBeanFactory}, all of the
 * operations in this interface will also check parent factories if this is a
 * {@link HierarchicalBeanFactory}. If a bean is not found in this factory instance,
 * the immediate parent factory will be asked. Beans in this factory instance
 * are supposed to override beans of the same name in any parent factory.
 * 与 ListableBeanFactory 中的方法不同的是，如果是一个HierarchicalBeanFactory，则该接口中的所有操作都会检查父工厂。
 * 如果一个 bean 在当前实工厂实例中没有找到，会立即访问父工厂。该工厂实例中的 bean 假定会重写存在于任何一个父工厂中的同名 bean。
 *
 * <p>Bean factory implementations should support the standard bean lifecycle interfaces
 * as far as possible. The full set of initialization methods and their standard order is:
 * bean 工厂的实现要尽可能支持标准 bean 生命周期接口。初始化方法的完整集合及顺序如下：
 * <ol>
 * <li>BeanNameAware's {@code setBeanName}
 * <li>BeanClassLoaderAware's {@code setBeanClassLoader}
 * <li>BeanFactoryAware's {@code setBeanFactory}
 * <li>EnvironmentAware's {@code setEnvironment}
 * <li>EmbeddedValueResolverAware's {@code setEmbeddedValueResolver}
 * <li>ResourceLoaderAware's {@code setResourceLoader}
 * (only applicable when running in an application context)
 * <li>ApplicationEventPublisherAware's {@code setApplicationEventPublisher}
 * (only applicable when running in an application context)
 * <li>MessageSourceAware's {@code setMessageSource}
 * (only applicable when running in an application context)
 * <li>ApplicationContextAware's {@code setApplicationContext}
 * (only applicable when running in an application context)
 * <li>ServletContextAware's {@code setServletContext}
 * (only applicable when running in a web application context)
 * <li>{@code postProcessBeforeInitialization} methods of BeanPostProcessors
 * <li>InitializingBean's {@code afterPropertiesSet}
 * <li>a custom {@code init-method} definition
 * <li>{@code postProcessAfterInitialization} methods of BeanPostProcessors
 * </ol>
 *
 * <p>On shutdown of a bean factory, the following lifecycle methods apply:
 * 关闭 bean 工厂时，需要调用以下生命周期方法：
 * <ol>
 * <li>{@code postProcessBeforeDestruction} methods of DestructionAwareBeanPostProcessors
 * <li>DisposableBean's {@code destroy}
 * <li>a custom {@code destroy-method} definition
 * </ol>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see BeanNameAware#setBeanName
 * @see BeanClassLoaderAware#setBeanClassLoader
 * @see BeanFactoryAware#setBeanFactory
 * @see org.springframework.context.EnvironmentAware#setEnvironment
 * @see org.springframework.context.EmbeddedValueResolverAware#setEmbeddedValueResolver
 * @see org.springframework.context.ResourceLoaderAware#setResourceLoader
 * @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher
 * @see org.springframework.context.MessageSourceAware#setMessageSource
 * @see org.springframework.context.ApplicationContextAware#setApplicationContext
 * @see org.springframework.web.context.ServletContextAware#setServletContext
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization
 * @see InitializingBean#afterPropertiesSet
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getInitMethodName
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#postProcessBeforeDestruction
 * @see DisposableBean#destroy
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName
 * @since 13 April 2001
 */
public interface BeanFactory {

	/**
	 * Used to dereference a {@link FactoryBean} instance and distinguish it from
	 * beans <i>created</i> by the FactoryBean. For example, if the bean named
	 * {@code myJndiObject} is a FactoryBean, getting {@code &myJndiObject}
	 * will return the factory, not the instance returned by the factory.
	 * 用于区分和识别一个 FactoryBean 实例及其创建的 bean。如，bean 名为 myJndiObject
	 * 的实例是一个 FactoryBean，则 &myJndiObject 会返回该工厂，而不是通过工厂返回的实例。
	 */
	String FACTORY_BEAN_PREFIX = "&";


	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>This method allows a Spring BeanFactory to be used as a replacement for the
	 * Singleton or Prototype design pattern. Callers may retain references to
	 * returned objects in the case of Singleton beans.
	 * 返回指定 bean 的一个实例，可能是共享的，也可能是独立的。这个方法允许 Spring BeanFactory 用作
	 * 单例或原型模式的替代。调用者在单例情况下可以保持对返回对象的引用。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 将别名翻译成对应的规范 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果在当前工厂实例中没有发现对应的 bean，则会查找父工厂。
	 *
	 * @param name the name of the bean to retrieve
	 * @return an instance of the bean.
	 * Note that the return value will never be {@code null} but possibly a stub for
	 * {@code null} returned from a factory method, to be checked via {@code equals(null)}.
	 * Consider using {@link #getBeanProvider(Class)} for resolving optional dependencies.
	 * 永远不会返回 null，但可能会返回一个表示 null 的存根对象，用于 equals(null) 检查。考虑使用
	 * #getBeanProvider(Class) 来解析可选依赖。
	 * @throws NoSuchBeanDefinitionException if there is no bean with the specified name
	 * @throws BeansException                if the bean could not be obtained
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * <p>Behaves the same as {@link #getBean(String)}, but provides a measure of type
	 * safety by throwing a BeanNotOfRequiredTypeException if the bean is not of the
	 * required type. This means that ClassCastException can't be thrown on casting
	 * the result correctly, as can happen with {@link #getBean(String)}.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 *
	 * @param name         the name of the bean to retrieve
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the bean.
	 * Note that the return value will never be {@code null}. In case of a stub for
	 * {@code null} from a factory method having been resolved for the requested bean, a
	 * {@code BeanNotOfRequiredTypeException} against the NullBean stub will be raised.
	 * Consider using {@link #getBeanProvider(Class)} for resolving optional dependencies.
	 * @throws NoSuchBeanDefinitionException  if there is no such bean definition
	 * @throws BeanNotOfRequiredTypeException if the bean is not of the required type
	 * @throws BeansException                 if the bean could not be created
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 返回指定 bean 的一个实例，可能是共享的，也可能是独立的。
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * 允许指定显式构造参数/工厂方法参数，重写 bean 定义中指定的默认参数（如果有的话）。
	 * Note that the provided arguments need to match a specific candidate constructor /
	 * factory method in the order of declared parameters.
	 * 注意，提供的参数要能匹配指定的候选构造器/工厂方法声明的参数顺序。
	 *
	 * @param name the name of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 *             (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException  if arguments have been given but
	 *                                       the affected bean isn't a prototype
	 * @throws BeansException                if the bean could not be created
	 * @since 2.5
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * Return the bean instance that uniquely matches the given object type, if any.
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the single bean matching the required type
	 * @throws NoSuchBeanDefinitionException   if no bean of the given type was found
	 * @throws NoUniqueBeanDefinitionException if more than one bean of the given type was found
	 * @throws BeansException                  if the bean could not be created
	 * @see ListableBeanFactory
	 * @since 3.0
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 返回指定 bean 的一个实例，可能是共享的，也可能是独立的。
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * Note that the provided arguments need to match a specific candidate constructor /
	 * factory method in the order of declared parameters.
	 * 允许指定显式构造参数/工厂方法参数，重写 bean 定义中指定的默认参数（如果有的话）。提供的参数要
	 * 能匹配指定的候选构造器/工厂方法声明的参数顺序。
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 * 这个方法进入 ListableBeanFactory 的按类型查找区域，但也可以基于给定类型的名字翻译成按名字查找。
	 * 要跨bean集进行更广泛的检索操作，请使用 ListableBeanFactory 和/或 BeanFactoryUtils。
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @param args         arguments to use when creating a bean instance using explicit arguments
	 *                     (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException  if arguments have been given but
	 *                                       the affected bean isn't a prototype
	 * @throws BeansException                if the bean could not be created
	 * @since 4.1
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * 返回给定bean的提供者，允许延迟按需检索实例，包括可用性和唯一性选项。
	 * <p>For matching a generic type, consider {@link #getBeanProvider(ResolvableType)}.
	 * 如果要匹配泛型类型，使用 getBeanProvider(ResolvableType)。
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return a corresponding provider handle
	 * @see #getBeanProvider(ResolvableType)
	 * @since 5.1
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options. This variant allows
	 * for specifying a generic type to match, similar to reflective injection points
	 * with generic type declarations in method/constructor parameters.
	 * 返回给定bean的提供者，允许延迟按需检索实例，包括可用性和唯一性选项。该变体方法允许指定一个匹配的泛型类型，
	 * 类似于在方法/构造器中使用泛型声明来反射拦截点。
	 * <p>Note that collections of beans are not supported here, in contrast to reflective
	 * injection points. For programmatically retrieving a list of beans matching a
	 * specific type, specify the actual bean type as an argument here and subsequently
	 * use {@link ObjectProvider#orderedStream()} or its lazy streaming/iteration options.
	 * bean 集合是不支持的，与反射注入点有冲突。为了程序能检查一个匹配给定类型的 bean 集合，在这里需要指定一个实际的
	 * bean 类型作为传入参数，然后使用 ObjectProvider#orderedStream() 或它的延迟流/迭代选项。
	 * <p>Also, generics matching is strict here, as per the Java assignment rules.
	 * For lenient fallback matching with unchecked semantics (similar to the 'unchecked'
	 * Java compiler warning), consider calling {@link #getBeanProvider(Class)} with the
	 * raw type as a second step if no full generic match is
	 * {@link ObjectProvider#getIfAvailable() available} with this variant.
	 * 而且，根据Java赋值规则，这里的泛型匹配是严格的。对于符合未检查语义（类似于“未检查”的Java编译器警告）的宽松回退方式，
	 * 如果在该方法调用ObjectProvider#getIfAvailable()时，泛型没有完全匹配，可考虑在下一步使用原生类型调用#getBeanProvider(Class)。
	 *
	 * @param requiredType type the bean must match; can be a generic type declaration
	 * @return a corresponding provider handle
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 * @since 5.1
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * Does this bean factory contain a bean definition or externally registered singleton
	 * instance with the given name?
	 * 判断该 bean 工厂是否包含一个 bean 定义或与给定名字匹配的已注册的外部单例实例。
	 * <p>If the given name is an alias, it will be translated back to the corresponding
	 * canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>If this factory is hierarchical, will ask any parent factory if the bean cannot
	 * be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 * <p>If a bean definition or singleton instance matching the given name is found,
	 * this method will return {@code true} whether the named bean definition is concrete
	 * or abstract, lazy or eager, in scope or not. Therefore, note that a {@code true}
	 * return value from this method does not necessarily indicate that {@link #getBean}
	 * will be able to obtain an instance for the same name.
	 * 如果找到一个与给定名字匹配的 bean 定义或单例，则方法返回 true，无论该 bean 定义是具体的还是抽象的，
	 * 饥饿加载的还是延迟加载的，或在作用域中。因此，该方法返回true时，并不表明getBean()会返回同名的一个实例。
	 *
	 * @param name the name of the bean to query
	 * @return whether a bean with the given name is present
	 */
	boolean containsBean(String name);

	/**
	 * Is this bean a shared singleton? That is, will {@link #getBean} always
	 * return the same instance?
	 * 判断该bean是否是一个共享单例，即 getBean() 总是返回同一实例。
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * independent instances. It indicates non-singleton instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isPrototype} operation to explicitly
	 * check for independent instances.
	 * 方法返回 false 不能清晰地表明是一个独立的实例，而表明的是非单例实例，也可以是一个作用域 bean。
	 * 使用isPrototype()操作可以显式检查是否为一个独立的实例。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name the name of the bean to query
	 * @return whether this bean corresponds to a singleton instance
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isPrototype
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Is this bean a prototype? That is, will {@link #getBean} always return
	 * independent instances?
	 * 判断该 bean 是否为原型模式，即 getBean()总是返回独立的实例。
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * a singleton object. It indicates non-independent instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isSingleton} operation to explicitly
	 * check for a shared singleton instance.
	 * 方法返回 fasle 并不能清晰地表明是一个单例对象，而表明的是一个非独立实例，也可以是一个作用域 bean。
	 * 使用isSingleton()操作可以显式检查是否为一个共享单例。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name the name of the bean to query
	 * @return whether this bean will always deliver independent instances
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isSingleton
	 * @since 2.0.3
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * 判断给定 bean 是否与给定类型匹配。更具体一点地说，检查使用一个给定名字对 getBean() 的调用，
	 * 是否返回与指定类型匹配的对象。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name        the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code ResolvableType})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #getType
	 * @since 4.2
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * 判断给定 bean 是否与给定类型匹配。更具体一点地说，检查使用一个给定名字对 getBean() 的调用，
	 * 是否返回与指定类型匹配的对象。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name        the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code Class})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #getType
	 * @since 2.0.1
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. This may lead to the initialization
	 * of a previously uninitialized {@code FactoryBean} (see {@link #getType(String, boolean)}).
	 * 返回给定名字的 bean 的类型。更具体一点的说法是，判断通过给定名调用 getBean() 返回的对象的类型。
	 * 对一个 FactoryBean，返回该 FactoryBean 创建的对象的类型，通过 FactoryBean#getObjectType() 暴露。
	 * 这可能会导致先前未初始化的 FactoryBean 被初始化（参考 getType(String, boolean)）。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * 如果给定名是一个别名，则会翻译成对应的规范的 bean 名。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name the name of the bean to query
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isTypeMatch
	 * @since 1.1.2
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. Depending on the
	 * {@code allowFactoryBeanInit} flag, this may lead to the initialization of a previously
	 * uninitialized {@code FactoryBean} if no early type information is available.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 *
	 * @param name                 the name of the bean to query
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 *                             just for the purpose of determining its object type
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isTypeMatch
	 * @since 5.2
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * Return the aliases for the given bean name, if any.
	 * 返回该指定 bean 名的别名，如果有的话。
	 * <p>All of those aliases point to the same bean when used in a {@link #getBean} call.
	 * 在 getBean() 调用中使用时，所有这些别名都指向同一个 bean。
	 * <p>If the given name is an alias, the corresponding original bean name
	 * and other aliases (if any) will be returned, with the original bean name
	 * being the first element in the array.
	 * 如果给定名是一个别名，则返回返回与之对应的原bean名和其他别名，原bean名在返回的数组中的第一个位置。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 如果工厂有继承层次，则在当前工厂实例中找不到时会查找所有父工厂。
	 *
	 * @param name the bean name to check for aliases
	 * @return the aliases, or an empty array if none
	 * @see #getBean
	 */
	String[] getAliases(String name);

}
