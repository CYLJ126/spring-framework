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

import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by objects used within a {@link BeanFactory} which
 * are themselves factories for individual objects. If a bean implements this
 * interface, it is used as a factory for an object to expose, not directly as a
 * bean instance that will be exposed itself.
 * 由 BeanFactory 中使用的对象实现的接口，这些对象本身就是单个对象的工厂。如果 bean 实现了这个接口，那么它就被用作要公开的对象工厂，
 * 而不是直接用作将要公开自身的 bean 实例。
 *
 * <p><b>NB: A bean that implements this interface cannot be used as a normal bean.</b>
 * A FactoryBean is defined in a bean style, but the object exposed for bean
 * references ({@link #getObject()}) is always the object that it creates.
 * 注意：实现此接口的 bean 不能用作普通 bean。FactoryBean 是以 bean 样式定义的，但是为 bean 引用公开的对象（通过 getObject() 返回）始终是它创建的对象。
 *
 * <p>FactoryBeans can support singletons and prototypes, and can either create
 * objects lazily on demand or eagerly on startup. The {@link SmartFactoryBean}
 * interface allows for exposing more fine-grained behavioral metadata.
 * FactoryBean 可以支持单例和原型，并且可以按需懒汉式创建对象，也可以在启动时饿汉式创建对象。SmartFactoryBean 接口允许公开更细粒度的行为元数据。
 *
 * <p>This interface is heavily used within the framework itself, for example for
 * the AOP {@link org.springframework.aop.framework.ProxyFactoryBean} or the
 * {@link org.springframework.jndi.JndiObjectFactoryBean}. It can be used for
 * custom components as well; however, this is only common for infrastructure code.
 * 这个接口在框架本身中被大量使用，例如用于 AOP ProxyFactoryBean 或 JndiObjectFactoryBean。
 * 它也可以用于自定义组件；但是，通常是在基本框架代码中。
 *
 * <p><b>{@code FactoryBean} is a programmatic contract. Implementations are not
 * supposed to rely on annotation-driven injection or other reflective facilities.</b>
 * FactoryBean 是一个程序化协定。实现类不应该依赖于注解驱动的注入或其他反射工具。
 * Invocations of {@link #getObjectType()} and {@link #getObject()} may arrive early
 * in the bootstrap process, even ahead of any post-processor setup. If you need access
 * to other beans, implement {@link BeanFactoryAware} and obtain them programmatically.
 * getObjectType() 和 getObject() 的调用可能在启动过程的早期被触发，甚至早于任何后置处理器设置。
 * 如果需要访问其他 bean，请实现 BeanFactoryAware 并以编程方式获取它们。
 *
 * <p><b>The container is only responsible for managing the lifecycle of the FactoryBean
 * instance, not the lifecycle of the objects created by the FactoryBean.</b> Therefore,
 * a destroy method on an exposed bean object (such as {@link java.io.Closeable#close()})
 * will <i>not</i> be called automatically. Instead, a FactoryBean should implement
 * {@link DisposableBean} and delegate any such close call to the underlying object.
 * 容器只负责管理 FactoryBean 实例的生命周期，而不负责 FactoryBean 创建的对象的生命周期。
 * 因此，公开的 bean 对象的销毁方法不会被自动调用（例如 Closeable#close()）。相反，FactoryBean 子类应该
 * 实现 DisposableBean 并将任何此类的关闭方法调用委托给底层对象。
 *
 * <p>Finally, FactoryBean objects participate in the containing BeanFactory's
 * synchronization of bean creation. Thus, there is usually no need for internal
 * synchronization other than for purposes of lazy initialization within the
 * FactoryBean itself (or the like).
 * 最后，FactoryBean 对象参与包含它的 BeanFactory 的 bean 创建同步。因此，除了在 FactoryBean 本身（或类似）内进行惰性初始化之外，通常不需要内部同步。
 *
 * @param <T> the bean type
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.aop.framework.ProxyFactoryBean
 * @see org.springframework.jndi.JndiObjectFactoryBean
 * @since 08.03.2003
 */
public interface FactoryBean<T> {

	/**
	 * The name of an attribute that can be
	 * {@link org.springframework.core.AttributeAccessor#setAttribute set} on a
	 * {@link org.springframework.beans.factory.config.BeanDefinition} so that
	 * factory beans can signal their object type when it cannot be deduced from
	 * the factory bean class.
	 * 可以被 AttributeAccessor#setAttribute 设置在 BeanDefinition 上的属性的名字，以便在工厂 bean
	 * 无法从工厂 bean 类推导它管理的 bean 对象类型时，可以知道其管理的 bean 类型。
	 *
	 * @since 5.2
	 */
	String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";


	/**
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * 返回该工厂管理的实例对象（可能是共享的也可能是独立的）。
	 * <p>As with a {@link BeanFactory}, this allows support for both the
	 * Singleton and Prototype design patterns.
	 * 与 BeanFactory 一起时，允许同时支持 Singleton 和 Prototype 设计模式。
	 * <p>If this FactoryBean is not fully initialized yet at the time of
	 * the call (for example because it is involved in a circular reference),
	 * throw a corresponding {@link FactoryBeanNotInitializedException}.
	 * 如果在调用时 FactoryBean 尚未完全初始化（如在循环引用中被调用），会抛出 FactoryBeanNotInitializedException 异常。
	 * <p>FactoryBeans are allowed to return {@code null} objects. The bean
	 * factory will consider this as a normal value to be used and will not throw
	 * a {@code FactoryBeanNotInitializedException} in this case. However,
	 * FactoryBean implementations are encouraged to throw
	 * {@code FactoryBeanNotInitializedException} themselves, as appropriate.
	 * FactoryBean 允许返回 null 对象。bean 工厂会将其视为正常值使用，这种情况下不会抛出
	 * FactoryBeanNotInitializedException 异常。然而，鼓励 FactoryBean 的实现类在合适的时候自己抛出
	 * FactoryBeanNotInitializedException 异常。
	 *
	 * @return an instance of the bean (can be {@code null})
	 * @throws Exception in case of creation errors
	 * @see FactoryBeanNotInitializedException
	 */
	@Nullable
	T getObject() throws Exception;

	/**
	 * Return the type of object that this FactoryBean creates,
	 * or {@code null} if not known in advance.
	 * 返回 FactoryBean 创建的对象的类型，如果不能提前知道类型，则返回 null。
	 * <p>This allows one to check for specific types of beans without
	 * instantiating objects, for example on autowiring.
	 * 允许在不实例化对象的时候检查 bean 的特定类型，如在自动注入当中。
	 * <p>In the case of implementations that create a singleton object,
	 * this method should try to avoid singleton creation as far as possible;
	 * it should rather estimate the type in advance.
	 * For prototypes, returning a meaningful type here is advisable too.
	 * 在创建单例对象的实现中，此方法应尽量避免单例创建；它应该提前估计类型。
	 * 对于原型，在这里返回一个有意义的类型也是可取的。
	 * <p>This method can be called <i>before</i> this FactoryBean has
	 * been fully initialized. It must not rely on state created during
	 * initialization; of course, it can still use such state if available.
	 * 该方法可以在 FactoryBean 完全初始化之前调用。它不能依赖于初始化期间创建的状态；当然，如果可用，它仍然可以使用这样的状态。
	 * <p><b>NOTE:</b> Autowiring will simply ignore FactoryBeans that return
	 * {@code null} here. Therefore, it is highly recommended to implement
	 * this method properly, using the current state of the FactoryBean.
	 * 注意：自动注入时会简单地忽略掉 FactoryBean 在这里返回的 null 值。
	 * 因此，强烈建议使用 FactoryBean 当前状态正确地实现该方法。
	 *
	 * @return the type of object that this FactoryBean creates,
	 * or {@code null} if not known at the time of the call
	 * @see ListableBeanFactory#getBeansOfType
	 */
	@Nullable
	Class<?> getObjectType();

	/**
	 * Is the object managed by this factory a singleton? That is,
	 * will {@link #getObject()} always return the same object
	 * (a reference that can be cached)?
	 * 被当前工厂管理的对象是否为单例？即 getObject() 是否总是返回同一对象（一个被缓存的引用）？
	 * <p><b>NOTE:</b> If a FactoryBean indicates that it holds a singleton
	 * object, the object returned from {@code getObject()} might get cached
	 * by the owning BeanFactory. Hence, do not return {@code true}
	 * unless the FactoryBean always exposes the same reference.
	 * 注意：如果一个 FactoryBean 表明它持有一个单例对象，则通过 getObject() 返回的对象可能会被拥有它的 BeanFactory 缓存。
	 * 因此，不要返回 true，除非 FactoryBean 总是暴露出去同一引用。
	 * <p>The singleton status of the FactoryBean itself will generally
	 * be provided by the owning BeanFactory; usually, it has to be
	 * defined as singleton there.
	 * FactoryBean 自己的单例状态会被提供给其所属的 BeanFactory；通常，它在那儿被定义为单例。
	 * <p><b>NOTE:</b> This method returning {@code false} does not
	 * necessarily indicate that returned objects are independent instances.
	 * An implementation of the extended {@link SmartFactoryBean} interface
	 * may explicitly indicate independent instances through its
	 * {@link SmartFactoryBean#isPrototype()} method. Plain {@link FactoryBean}
	 * implementations which do not implement this extended interface are
	 * simply assumed to always return independent instances if the
	 * {@code isSingleton()} implementation returns {@code false}.
	 * 注意：此方法返回 false 并不一定表示返回的对象是独立的实例。扩展接口 SmartFactoryBean 的实现
	 * 可能会通过 SmartFactoryBean#isPrototype() 显式指明这是一个独立的实例。原生的 FactoryBean
	 * 实现类不会实现它的扩展接口，仅简单地假设在 isSingleton() 返回 false 时，总是返回一个独立实例。
	 * <p>The default implementation returns {@code true}, since a
	 * {@code FactoryBean} typically manages a singleton instance.
	 * 默认实现返回 true，因为一个 FactoryBean 通常用于管理一个单例实例。
	 *
	 * @return whether the exposed object is a singleton
	 * @see #getObject()
	 * @see SmartFactoryBean#isPrototype()
	 */
	default boolean isSingleton() {
		return true;
	}

}
