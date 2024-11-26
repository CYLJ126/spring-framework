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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 * 共享 bean 实例的通用注册表，实现了 SingletonBeanRegistry 接口。允许注册被注册表的所有调用者共享的单例实例，以通过 bean 名称获取。
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 * 也支持 DisposableBean 实例的注册（可能与已注册的单例对应，也可能不对应），以在注册表关闭时被销毁。
 * bean 之间的依赖可被注册，以确保恰当的关闭顺序。
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 * 这个类主要是作为 BeanFactory 接口实现类的基类使用，将单例 bean 实例的通用管理分解出来。
 * 注意 ConfigurableBeanFactory 接口继承了 SingletonBeanRegistry 接口。
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 * 注意，这个类既不假设 bean 定义概念，也不假设 bean 实例的特定创建过程，这与 AbstractBeanFactory 和
 * DefaultListableBeanFactory（继承自它）相反。也可以用作要代理的嵌套帮助类。
 *
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**
	 * Maximum number of suppressed exceptions to preserve.
	 * 要保留的已抑制异常的最大数量。
	 */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;

	/**
	 * Cache of singleton objects: bean name to bean instance.
	 * 单例缓存映射集，Map<bean 名，bean 实例>
	 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * Creation-time registry of singleton factories: bean name to ObjectFactory.
	 * 单例工厂的创建时注册表：Map<bean 名，工厂>，创建完单例即从该集合中删除并添加到 earlySingletonObjects 集合中
	 */
	private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(16);

	/**
	 * Custom callbacks for singleton creation/registration.
	 * 单例创建/注册的自定义回调方法，Map<bean 名，回调方法>。
	 */
	private final Map<String, Consumer<Object>> singletonCallbacks = new ConcurrentHashMap<>(16);

	/**
	 * Cache of early singleton objects: bean name to bean instance.
	 * 提早暴露的单例缓存映射集，Map<bean 名，bean 实例>，从 singletonFactories 中删除并同时添加进来。
	 * 其意义在于，如解决循环依赖时，会将 bean 提前暴露出去，此时，将其从实例工厂中获取，然后放到该集合中，表示提早暴露的 bean，
	 * 然后在此 bean 真正初始化时，从此集合中删除，并放到 registeredSingletons 中。
	 */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/**
	 * Set of registered singletons, containing the bean names in registration order.
	 * 已注册的单例集合，即按注册顺序存放的 bean 名集合。
	 */
	private final Set<String> registeredSingletons = Collections.synchronizedSet(new LinkedHashSet<>(256));

	/**
	 * 单例锁，在注册或创建、销毁单例时，需加锁。
	 * 按工厂加锁，而不是按单例创建来加锁，是因为创建一个 bean 时，会先创建它依赖的 bean，如果不这样加锁，会有冲突的风险，
	 * 参见 {@link DefaultSingletonBeanRegistry#getSingleton(String, ObjectFactory)}。
	 */
	private final Lock singletonLock = new ReentrantLock();

	/**
	 * Names of beans that are currently in creation.
	 * 正在创建当中的 bean 的名字集合。
	 */
	private final Set<String> singletonsCurrentlyInCreation = ConcurrentHashMap.newKeySet(16);

	/**
	 * Names of beans currently excluded from in creation checks.
	 * 作 bean 是否在创建中的检查时，排除在外的 bean。
	 */
	private final Set<String> inCreationCheckExclusions = ConcurrentHashMap.newKeySet(16);

	/**
	 * Flag that indicates whether we're currently within destroySingletons.
	 * 表示我们当前是否在 destroySingleton 中。在销毁 bean 时置为 true，销毁结束时置为 false。
	 */
	private volatile boolean singletonsCurrentlyInDestruction = false;

	/**
	 * Collection of suppressed Exceptions, available for associating related causes.
	 * 被抑制的异常的集合，可用于关联相关引发原因（cause）。
	 * 每次创建单例前初始化，创建完后还原为 null，见 {@link DefaultSingletonBeanRegistry#getSingleton(String, ObjectFactory)}。
	 */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/**
	 * Disposable bean instances: bean name to disposable instance.
	 * DisposableBean 实例集合，<bean 名，DisposableBean 实例>。TODO，用处
	 */
	private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();

	/**
	 * Map between containing bean names: bean name to Set of bean names that the bean contains.
	 * bean 名到 bean 所包含的集合的映射集，<bean 名，该 bean 名包含的 bean 名集合>
	 */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/**
	 * Map between dependent bean names: bean name to Set of dependent bean names.
	 * bean 到依赖此 bean 的集合的映射集，<bean 名，依赖此 bean 的 bean 集合>
	 */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/**
	 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
	 * bean 到其依赖的 bean 的集合的映射集，<bean 名，该 bean 名依赖的 bean 名集合>
	 */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		// 加锁，无锁则睡眠，直到获得锁
		this.singletonLock.lock();
		try {
			// 添加单例到该注册表中
			addSingleton(beanName, singletonObject);
		} finally {
			this.singletonLock.unlock();
		}
	}

	/**
	 * Add the given singleton object to the singleton registry.
	 * 添加给定的单例对象到注册表中。
	 * <p>To be called for exposure of freshly registered/created singletons.
	 * 要暴露刚刚已注册/创建的单例时被调用。
	 *
	 * @param beanName        the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		Object oldObject = this.singletonObjects.putIfAbsent(beanName, singletonObject);
		if (oldObject != null) {
			// 重复添加则抛异常
			throw new IllegalStateException("Could not register object [" + singletonObject +
					"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
		}
		// 移除
		this.singletonFactories.remove(beanName);
		this.earlySingletonObjects.remove(beanName);
		// 添加
		this.registeredSingletons.add(beanName);

		Consumer<Object> callback = this.singletonCallbacks.get(beanName);
		if (callback != null) {
			// 回调方法
			callback.accept(singletonObject);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * 为指定 bean 添加单例工厂，如果需要的话。
	 * <p>To be called for early exposure purposes, for example, to be able to
	 * resolve circular references.
	 * 为了早期暴露出去而被调用，如为了能够解析循环引用。
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		// 放入工厂
		this.singletonFactories.put(beanName, singletonFactory);
		// 删除
		this.earlySingletonObjects.remove(beanName);
		// 添加
		this.registeredSingletons.add(beanName);
	}

	@Override
	public void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer) {
		this.singletonCallbacks.put(beanName, singletonConsumer);
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		// 允许早期引用
		return getSingleton(beanName, true);
	}

	/**
	 * Return the (raw) singleton object registered under the given name.
	 * 返回给定名注册的（原始）单例对象。
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * 检查已实例化的单例，且允许对当前已创建的单例的早期引用（正在解析循环引用）。
	 *
	 * @param beanName            the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock.
		// 不带锁的情况下快速检查单例 bean 是否已存在
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 检查早期单例 bean 是否已存在
			singletonObject = this.earlySingletonObjects.get(beanName);
			// 指定的单例 bean 不存在，且允许早期引用
			if (singletonObject == null && allowEarlyReference) {
				if (!this.singletonLock.tryLock()) {
					// Avoid early singleton inference outside of original creation thread.
					// 避免在原始创建线程之外进行早期单例返回
					return null;
				}
				try {
					// Consistent creation of early reference within full singleton lock.
					// 在完全单例锁中创建早期引用
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							// 获取对应的单例工厂
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 从工厂中获取 bean 单例
								singletonObject = singletonFactory.getObject();
								// Singleton could have been added or removed in the meantime.
								// 将工厂从 singletonFactories 中删除，并添加到 earlySingletonObjects 中
								if (this.singletonFactories.remove(beanName) != null) {
									this.earlySingletonObjects.put(beanName, singletonObject);
								} else {
									// 如果 singletonFactories 删除失败，说明有线程创建好了单例，可直接获得
									singletonObject = this.singletonObjects.get(beanName);
								}
							}
						}
					}
				} finally {
					this.singletonLock.unlock();
				}
			}
		}
		return singletonObject;
	}

	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * 返回与给定名对应的已注册的原生单例，没有则创建并注册一个新的。
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 *                         with, if necessary
	 * @return the registered singleton object
	 */
	@SuppressWarnings("NullAway")
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");

		// 是否可持有锁，默认返回 null，即 acquireLock 为 true，可获取锁
		Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
		boolean acquireLock = !Boolean.FALSE.equals(lockFlag);
		// 是否获得了锁，获取不加锁，因为 singletonObjects 是个 ConcurrentHashMap，创建需要加锁
		boolean locked = (acquireLock && this.singletonLock.tryLock());
		try {
			// 获取到则直接返回
			Object singletonObject = this.singletonObjects.get(beanName);
			// 获取不到则创建
			if (singletonObject == null) {
				if (acquireLock && !locked) {
					if (Boolean.TRUE.equals(lockFlag)) {
						// Another thread is busy in a singleton factory callback, potentially blocked.
						// Fallback as of 6.2: process given singleton bean outside of singleton lock.
						// Thread-safe exposure is still guaranteed, there is just a risk of collisions
						// when triggering creation of other beans as dependencies of the current bean.
						// 另一个线程在单例工厂回调中，处于繁忙状态，可能被锁住了。
						// 从 6.2 开始的回退：在单例锁之外处理给定的单例 bean。线程安全的暴露仍然是得到了保证的，只是在
						// 触发当前 bean 所依赖的 bean 的创建时，会存在冲突的风险。
						if (logger.isInfoEnabled()) {
							logger.info("Creating singleton bean '" + beanName + "' in thread \"" +
									Thread.currentThread().getName() + "\" while other thread holds " +
									"singleton lock for other beans " + this.singletonsCurrentlyInCreation);
						}
					} else {
						// No specific locking indication (outside a coordinated bootstrap) and
						// singleton lock currently held by some other creation method -> wait.
						// 如果没锁上（在协调的引导线程之外），该单例锁被其他创建线程持有 -> 等待。
						this.singletonLock.lock();
						locked = true;
						// Singleton object might have possibly appeared in the meantime.
						// 等待期间，可能单例实例已经创建好了，这里重新获取一下。
						singletonObject = this.singletonObjects.get(beanName);
						if (singletonObject != null) {
							return singletonObject;
						}
					}
				}

				if (this.singletonsCurrentlyInDestruction) {
					// 如果是在单例销毁期间，则抛出 BeanCreationNotAllowedException 异常
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
									"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 单例创建之前的回调处理
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (locked && this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					// 初始化被抑制的异常集合
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 从工厂获取单例对象
					singletonObject = singletonFactory.getObject();
					// 如果是从工厂获取的，标记为是一个新的单例
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					// 此时是否已经出现了一个单例对象 -> 如果是，继续处理它，因为异常指示该状态。
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						// 如果不是由于在同一时间其他线程生成了该单例，则向外抛出异常
						throw ex;
					}
				} catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							// TODO 为什么是对每个抑制异常都添加？
							ex.addRelatedCause(suppressedException);
						}
					}
					// 向外抛出异常
					throw ex;
				} finally {
					if (recordSuppressedExceptions) {
						// 复原，说明 suppressedExceptions 是对每次生成一个单例的抑制异常
						this.suppressedExceptions = null;
					}
					// 单例创建后的回调处理
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					// 如果是一个新的单例，则添加对象到注册表中
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		} finally {
			if (locked) {
				// 解锁
				this.singletonLock.unlock();
			}
		}
	}

	/**
	 * Determine whether the current thread is allowed to hold the singleton lock.
	 * 判断当前线程是否允许持有单例锁。
	 * <p>By default, any thread may acquire and hold the singleton lock, except
	 * background threads from {@link DefaultListableBeanFactory#setBootstrapExecutor}.
	 * 默认地，任何线程都可以获取并持有单例锁，除了来自 DefaultListableBeanFactory#setBootstrapExecutor 的后台线程。
	 *
	 * @return {@code false} if the current thread is explicitly not allowed to hold
	 * the lock, {@code true} if it is explicitly allowed to hold the lock but also
	 * accepts lenient fallback behavior, or {@code null} if there is no specific
	 * indication (traditional behavior: always holding a full lock)
	 * @since 6.2
	 */
	@Nullable
	protected Boolean isCurrentThreadAllowedToHoldSingletonLock() {
		return null;
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, for example, a temporary circular reference resolution problem.
	 * 注册在创建单例 bean 实例期间产生的被抑制的异常，例如，临时循环引用解析问题。
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * 默认实现在此注册表的抑制异常集合中保留任何给定的异常，最多保留 100 个异常，将它们作为相关原因添加到最终的顶级 BeanCreationException 中。
	 *
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
			this.suppressedExceptions.add(ex);
		}
	}

	/**
	 * Remove the bean with the given name from the singleton registry, either on
	 * regular destruction or on cleanup after early exposure when creation failed.
	 * 从单例注册表中删除指定 bean，无论是常规销毁，还是清空创建失败时提前暴露的 bean。
	 *
	 * @param beanName the name of the bean
	 */
	protected void removeSingleton(String beanName) {
		this.singletonObjects.remove(beanName);
		this.singletonFactories.remove(beanName);
		this.earlySingletonObjects.remove(beanName);
		this.registeredSingletons.remove(beanName);
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		return StringUtils.toStringArray(this.registeredSingletons);
	}

	@Override
	public int getSingletonCount() {
		return this.registeredSingletons.size();
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		} else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * 判断指定的单例是否正在创建当中（在整个工厂内）。
	 *
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * Callback before singleton creation.
	 * 单例创建之前的回调方法。
	 * <p>The default implementation register the singleton as currently in creation.
	 * 默认实现为将该 bean 名标记为正在创建中（放入 singletonsCurrentlyInCreation 集合）。
	 *
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		// 不在创建当中（inCreationCheckExclusions），且在创建当中（singletonsCurrentlyInCreation）的 bean
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			// 并发创建异常
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * 单例创建后的回调。
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * 默认实现为去除其在创建中的标记（从 singletonsCurrentlyInCreation 移除）。
	 *
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			// 非法状态异常，说明 bean 当前并不在创建中
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * 向注册表中添加给定的 Disposable bean.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * Disposable bean 通常与已注册的 bean 对应，名字一样但非同一实例（如，一个 DisposableBean 与一个不是正常实现自 Spring 的
	 * DisposableBean 接口的单例的 DisposableBean 适配器）。
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * for example, between an inner bean and its containing outer bean.
	 * 注册两个 bean 之间的包含关系，例如，内部 bean 与其包含的外部 bean 之间的包含关系。
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * 此外，会将 inner bean 注册到依赖 outer bean 的依赖中，作为销毁顺序依据。
	 *
	 * @param containedBeanName  the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			// 注册失败，则直接返回，不抛异常
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		// 注册依赖关系，作为销毁顺序依据
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * 注册依赖关系，即 beanName 依赖的 bean，依赖 bean 要在给定 bean 销毁前被销毁。
	 *
	 * @param beanName          the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			// 注册依赖关系
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			// 注册被依赖关系
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * 判断指定的依赖 Bean 是否依赖于给定的 Bean 或其任何传递依赖项，即 dependentBeanName 是否依赖于 beanName。
	 *
	 * @param beanName          the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		// 加同步锁
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		// 拿到依赖 beanName 的 bean 集合
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null || dependentBeans.isEmpty()) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			// 包含 dependentBeanName 则返回 true
			return true;
		}
		if (alreadySeen == null) {
			alreadySeen = new HashSet<>();
		}
		alreadySeen.add(beanName);
		for (String transitiveDependency : dependentBeans) {
			// 依赖 bean 的链式检查，即依赖此 bean 的 bean 列表中的每个 transitiveDependency，
			// 是否依赖 transitiveDependency 的 bean 中包含 dependentBeanName
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * 判断是否有 bean 依赖指定的 bean。
	 *
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * 返回依赖此 bean 的 bean 列表，如果有的话。
	 *
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		// 加锁，返回一个副本
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * 返回指定 bean 依赖的 bean 列表，如果有的话。
	 *
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		// 加锁，返回一个副本
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		// 修改状态，说明处于单例销毁过程
		this.singletonsCurrentlyInDestruction = true;

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			// 加锁获取一次性单例列表
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			// 销毁
			destroySingleton(disposableBeanNames[i]);
		}

		// 清空相关集合
		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		// 加单例锁
		this.singletonLock.lock();
		try {
			// 删除单例缓存
			clearSingletonCache();
		} finally {
			// 解锁
			this.singletonLock.unlock();
		}
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * 清除注册表中所有缓存的单例实例。
	 *
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		// 清空单例相关集合
		this.singletonObjects.clear();
		this.singletonFactories.clear();
		this.earlySingletonObjects.clear();
		this.registeredSingletons.clear();
		// 删除完成，标记为已不在销毁过程当中
		this.singletonsCurrentlyInDestruction = false;
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * 销毁指定 bean，如果发现此 bean 有一个对应的 disposable bean 则代理到 destroyBean 方法。
	 *
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Destroy the corresponding DisposableBean instance.
		// This also triggers the destruction of dependent beans.
		// 销毁对应的 DisposableBean 实例。这也会触发依赖该 bean 的那些 bean 的销毁
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);

		// destroySingletons() removes all singleton instances at the end,
		// leniently tolerating late retrieval during the shutdown phase.
		// 最后 destroySingletons()（即销毁所有单例） 会删除所有单例实例，容忍关闭阶段的延迟检索。即在此不做任何事
		// 如果不是在单例销毁期间，则删除指定 bean
		if (!this.singletonsCurrentlyInDestruction) {
			// For an individual destruction, remove the registered instance now.
			// As of 6.2, this happens after the current bean's destruction step,
			// allowing for late bean retrieval by on-demand suppliers etc.
			// 对于单个销毁，请立即删除已注册的实例。从 6.2 开始，这发生在当前 bean 的销毁步骤之后，允许按需的 supplier 进行延迟 bean 检索等。
			this.singletonLock.lock();
			try {
				// 从单例注册表中删除指定 bean
				removeSingleton(beanName);
			} finally {
				this.singletonLock.unlock();
			}
		}
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * 销毁给定的 bean。必须先销毁依赖此 bean 的 bean，再销毁当前 bean 本身。不应该抛出异常。
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		// 触发依赖此 bean 的 bean 列表的销毁
		Set<String> dependentBeanNames;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			// 在完全同步范围内，以保证 Set 断开连接（仅同步获取 Set 的操作，删除操作不在同步范围内）
			dependentBeanNames = this.dependentBeanMap.remove(beanName);
		}
		if (dependentBeanNames != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
			}
			for (String dependentBeanName : dependentBeanNames) {
				// 销毁
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				// 实际销毁给定的 bean
				bean.destroy();
			} catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		// 触发其包含的 bean 集合的销毁
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			// 在完全同步范围内，以保证 Set 断开连接（仅同步获取 Set 的操作，删除操作不在同步范围内）
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				// 销毁
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		// 删除依赖此 bean 的 bean 集合
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		// 删除此 bean 依赖的 bean 集合
		this.dependenciesForBeanMap.remove(beanName);
	}

	@Deprecated(since = "6.2")
	@Override
	public final Object getSingletonMutex() {
		return new Object();
	}

}
