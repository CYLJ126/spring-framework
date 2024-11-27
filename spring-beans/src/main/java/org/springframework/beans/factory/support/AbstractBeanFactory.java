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

import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.*;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.beans.PropertyEditor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 * BeanFactory 接口的抽象实现，提供了 ConfigurableBeanFactory SPI 的完整功能。
 * 不假定这是一个可列举的 bean 工厂：因此也可以用作 bean 工厂实现的基类，这些实现从某些后端资源
 * 获取 bean 定义（其中 bean 定义访问是一项昂贵的操作）。
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 * 这个类提供了一个单例缓存（通过其基类 DefaultSingletonBeanRegistry、singleton/prototype 计算、
 * FactoryBean 处理、别名、子 bean 定义的 bean 定义合并和 bean 销毁（DisposableBean 接口、自定义销毁方法）。
 * 此外，它可以通过实现 HierarchicalBeanFactory 接口来管理 bean 工厂层次结构（在未知 bean 的情况下委托给父 bean）。
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 * 子类要继承的主要模板方法是 getBeanDefinition() 和 createBean()，根据一个给定的 bean 名进行
 * bean 定义检索，以及根据给定的 bean 定义创建一个 bean 实例。这些操作的默认实现可以在
 * DefaultListableBeanFactory 和 AbstractAutowireCapableBeanFactory 中找到。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 * @since 15 April 2001
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/**
	 * Parent bean factory, for bean inheritance support.
	 * 父 bean 工厂，支持 bean 的继承层次结构。
	 */
	@Nullable
	private BeanFactory parentBeanFactory;

	/**
	 * ClassLoader to resolve bean class names with, if necessary.
	 * 解析 bean 类型名的类加载器（如果需要的话）。
	 */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/**
	 * ClassLoader to temporarily resolve bean class names with, if necessary.
	 * 解析 bean 类型名的临时类加载器（如果需要的话）。
	 * 在做类型检查，又不想创建实例时，会用到临时类加载器。
	 */
	@Nullable
	private ClassLoader tempClassLoader;

	/**
	 * Whether to cache bean metadata or rather reobtain it for every access.
	 * 决定缓存 bean 元数据还是每次访问时重新获取。
	 */
	private boolean cacheBeanMetadata = true;

	/**
	 * Resolution strategy for expressions in bean definition values.
	 * bean 定义中的值中存在的表达式的解析器（策略）。
	 */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/**
	 * Spring ConversionService to use instead of PropertyEditors.
	 * 用于替代 PropertyEditors 的 Spring 转换服务，用于处理属性。
	 */
	@Nullable
	private ConversionService conversionService;

	/**
	 * Custom PropertyEditorRegistrars to apply to the beans of this factory.
	 * 应用于该工厂的 bean 的自定义的 PropertyEditorRegistrar 集合。
	 */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/**
	 * Custom PropertyEditors to apply to the beans of this factory.
	 * 应用于该工厂的 bean 的自定义的 PropertyEditors 集合。
	 */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/**
	 * A custom TypeConverter to use, overriding the default PropertyEditor mechanism.
	 * 自定义的 TypeConverter，重写默认的 PropertyEditor 机制。
	 */
	@Nullable
	private TypeConverter typeConverter;

	/**
	 * String resolvers to apply, for example, to annotation attribute values.
	 * 使用的字符串解析器列表，如用于注解属性值的处理。
	 */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/**
	 * BeanPostProcessors to apply.
	 * 使用的 BeanPostProcessor 列表。
	 */
	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	/**
	 * Cache of pre-filtered post-processors.
	 * 预过滤后置处理器缓存。
	 */
	@Nullable
	private BeanPostProcessorCache beanPostProcessorCache;

	/**
	 * Map from scope identifier String to corresponding Scope.
	 * Scope 作用域 Map 集，键为 作用域标志。
	 */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/**
	 * Application startup metrics.
	 * 应用程序启动指标。
	 */
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/**
	 * Map from bean name to merged RootBeanDefinition.
	 * 合并的 RootBeanDefinition Map 集合，键为 bean 名。
	 */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/**
	 * Names of beans that have already been created at least once.
	 * 至少被创建过一次的 bean 的名称 Set 集合。
	 */
	private final Set<String> alreadyCreated = ConcurrentHashMap.newKeySet(256);

	/**
	 * Names of beans that are currently in creation.
	 * 线程本地变量，存放当前正在创建的 bean 的名称。
	 */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");

	/**
	 * Create a new AbstractBeanFactory.
	 * 无参构造器
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * 使用给定的父 Bean 工厂创建一个新的 Bean 工厂。
	 *
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface - BeanFactory 接口的实现
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 返回指定 bean 的实例，该实例可以是共享的也可以是独立的。
	 *
	 * @param name         the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args         arguments to use when creating a bean instance using explicit arguments
	 *                     (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 返回指定 bean 的实例，该实例可以是共享的也可以是独立的。
	 * 1. 作饥饿检查，如果存在 bean 实例，则直接获取；
	 * 2. 如果不存在：
	 * |-2.1 如果当前工厂不包含 bean 定义，则从父工厂返回；
	 * |-2.2 如果存在 bean 定义，则：
	 * |--2.2.1 先处理并获取 bean 定义；
	 * |--2.2.2 初始化依赖的 bean；
	 * |--2.2.3 根据 bean 是否为单例、原型、作用域 bean 分别创建；
	 * 3. 创建实例进行类型转换，适配指定的所需类型，并返回；
	 *
	 * @param name          the name of the bean to retrieve - bean 名
	 * @param requiredType  the required type of the bean to retrieve - bean 类型
	 * @param args          arguments to use when creating a bean instance using explicit arguments
	 *                      (only applied when creating a new instance as opposed to retrieving an existing one)
	 *                      创建 bean 实例时使用的显式参数（仅在创建新实例时应用，而不是检索现有实例）
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 *                      not for actual use - 获取实例是否只是为了类型检查而不是实际使用
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {
		// 先将传入的 bean 名解析为规范的 bean 名
		String beanName = transformedBeanName(name);
		Object beanInstance;

		// Eagerly check singleton cache for manually registered singletons.
		// 饥饿检查手动注册的单例缓存。返回给定名注册的（原始）单例对象。
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				// 单例 bean 是否在创建当中
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				} else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			// 获取 bean 实例
			beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		} else {
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			// 如果是原型 bean 且存在创建当中，则抛异常：可能处于循环引用中。
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.
			// 检查工厂中是否存在 bean 定义。
			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 父工厂不为 null 且当前工厂不包含该 bean 定义，则递归检查父工厂（向上查找）
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory abf) {
					return abf.doGetBean(nameToLookup, requiredType, args, typeCheckOnly);
				} else if (args != null) {
					// Delegation to parent with explicit args.
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				} else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				} else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			if (!typeCheckOnly) {
				// 标记指定的 bean 为已创建（或即将创建）。
				markBeanAsCreated(beanName);
			}

			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {
					beanCreation.tag("beanType", requiredType::toString);
				}
				// 获取 bean 定义
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				// 检查给定的合并 bean 定义是否是抽象的，如果是，则抛出 bean 为抽象异常。
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.
				// 确保依赖的 bean 已初始化。
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						// 判断循环依赖，注意，调用的是 DefaultSingletonBeanRegistry.isDependent()，说明是单例，
						// 因为原型 bean 在判断有循环依赖时已经在上面抛出异常了
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注册依赖关系
						registerDependentBean(dep, beanName);
						try {
							// 创建依赖 bean，调用了本方法，先确保依赖的 bean 被创建了
							getBean(dep);
						} catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						} catch (BeanCreationException ex) {
							if (requiredType != null) {
								// Wrap exception with current bean metadata but only if specifically
								// requested (indicated by required type), not for depends-on cascades.
								throw new BeanCreationException(mbd.getResourceDescription(), beanName,
										"Failed to initialize dependency '" + ex.getBeanName() + "' of " +
												requiredType.getSimpleName() + " bean '" + beanName + "': " +
												ex.getMessage(), ex);
							}
							throw ex;
						}
					}
				}

				// Create bean instance.
				// 创建 bean
				if (mbd.isSingleton()) {
					// 如果是单例
					// 返回与给定名对应的已注册的原生单例，没有则创建并注册一个新的。lambda 参数为对象工厂 ObjectFactory
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// bean 创建
							return createBean(beanName, mbd, args);
						} catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							// 从单例缓存中显式删除实例：它可能已被创建过程急切地放在那里，以允许循环引用解析。此外，删除所有得到的对该 Bean 的临时引用的 Bean。
							destroySingleton(beanName);
							throw ex;
						}
					});
					// 获取给定 bean 实例的对象，可以是 bean 实例本身，也可以是 FactoryBean 创建的对象。
					beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				} else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					// 原型模式，创建一个新的实例
					Object prototypeInstance = null;
					try {
						// 创建前处理，将其标记为正在创建中
						beforePrototypeCreation(beanName);
						// bean 创建
						prototypeInstance = createBean(beanName, mbd, args);
					} finally {
						// 创建后处理，标记为不在创建中
						afterPrototypeCreation(beanName);
					}
					// 获取给定 bean 实例的对象，可以是 bean 实例本身，也可以是 FactoryBean 创建的对象。
					beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				} else {
					// 不是单例 bean 也不是原型 bean，则获取作用域，通过作用域创建
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
					}
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							// 与原型 bean 一致的创建前处理
							beforePrototypeCreation(beanName);
							try {
								// bean 创建
								return createBean(beanName, mbd, args);
							} finally {
								// 与原型 bean 一致的创建后处理
								afterPrototypeCreation(beanName);
							}
						});
						// 获取给定 bean 实例的对象，可以是 bean 实例本身，也可以是 FactoryBean 创建的对象。
						beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					} catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			} catch (BeansException ex) {
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			} finally {
				beanCreation.end();
				if (!isCacheBeanMetadata()) {
					clearMergedBeanDefinition(beanName);
				}
			}
		}
		// 适配所需类型，作类型转换
		return adaptBeanInstance(name, beanInstance, requiredType);
	}

	@SuppressWarnings("unchecked")
	<T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
		// Check if required type matches the type of the actual bean instance.
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return (T) convertedBean;
			} catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}

	@Override
	public boolean containsBean(String name) {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);
		// 如果包含该单例或 bean 定义
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			// 不是一个工厂间接引用，或是一个工厂 bean，则返回 true
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// Not found -> check parent.
		// 如果不存在，则向父类查找
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);
		// 获取单例
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean<?> factoryBean) {
				// 如果是 FactoryBean，且是工厂的间接引用或 FactoryBean 管理的对象为单例
				return (BeanFactoryUtils.isFactoryDereference(name) || factoryBean.isSingleton());
			} else {
				// 不是 FactoryBean，且不是工厂的间接引用
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		// 未发现单例实例 -> 检查 bean 定义
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 当前工厂中未找到 bean 定义 -> 从父工厂查找
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		// 获取合并的 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		// 对于 FactoryBean，如果不是间接引用，则返回已创建对象的单例状态。
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				// 是 FactoryBean，且是工厂间接引用，直接返回 true
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				// 获取 FactoryBean bean 实例，判断其管理的对象是否为单例
				return factoryBean.isSingleton();
			} else {
				// 不是 FactoryBean，且不是工厂的间接引用
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		} else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 当前工厂中未找到 bean 定义 -> 从父工厂查找
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		// 获取合并的 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			// 对于 FactoryBean，如果不是间接引用，则返回已创建对象的单例状态。
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		// 单例或作用域 bean - 非原型 bean
		// 然而，FactoryBean 可能仍会产生一个原型对象
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			// 如果该 FactoryBean 是 SmartFactoryBean 且是原型，或该 FactoryBean 非单例
			return ((fb instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isPrototype()) ||
					!fb.isSingleton());
		} else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * isTypeMatch(String, ResolvableType) 的内部扩展变体方法，判断给定的 bean 名与类型是否匹配。
	 * 允许应用额外约束，以确保不会提前创建 bean。
	 *
	 * @param name        the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 *                    {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #getType
	 * @since 5.2
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);
		// 是否为间接引用
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		// 手工检查已注册单例，不为 null 则返回的类型
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			// Determine target for FactoryBean match if necessary.
			// 如果 bean 实例是 FactoryBean，进行相应匹配
			if (beanInstance instanceof FactoryBean<?> factoryBean) {
				if (!isFactoryDereference) {
					// 获取该 FactoryBean 管理的 bean 的类型
					Class<?> type = getTypeForFactoryBean(factoryBean);
					if (type == null) {
						// 返回 false
						return false;
					}
					if (typeToMatch.isAssignableFrom(type)) {
						// 类型匹配
						return true;
					} else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
						// 待匹配类型中存在泛型，且有此 bean 定义
						// 获取 bean 定义
						RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
						// 目标类型
						ResolvableType targetType = mbd.targetType;
						if (targetType == null) {
							// 工厂方法返回类型
							targetType = mbd.factoryMethodReturnType;
						}
						if (targetType == null) {
							// 返回 false
							return false;
						}
						Class<?> targetClass = targetType.resolve();
						// 如果是 FactoryBean
						if (targetClass != null && FactoryBean.class.isAssignableFrom(targetClass)) {
							Class<?> classToMatch = typeToMatch.resolve();
							if (classToMatch != null && !FactoryBean.class.isAssignableFrom(classToMatch) &&
									!classToMatch.isAssignableFrom(targetType.toClass())) {
								// TODO 这里是怎么判断FactoryBean和Generic的
								return typeToMatch.isAssignableFrom(targetType.getGeneric());
							}
						} else {
							return typeToMatch.isAssignableFrom(targetType);
						}
					}
					return false;
				}
			} else if (isFactoryDereference) {
				// 如果是间接引用
				return false;
			}

			// Actual matching against bean instance...
			// bean 实例的真正匹配
			if (typeToMatch.isInstance(beanInstance)) {
				// Direct match for exposed instance?
				// 暴露的实例直接能匹配上
				return true;
			} else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
				// Generics potentially only match on the target class, not on the proxy...
				// 泛型可能只在目标类上匹配，而在代理上不匹配......
				// 获取 bean 定义
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				// 目标类型
				Class<?> targetType = mbd.getTargetType();
				// 目标类型不等于 bean 实例的用户定义类
				if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
					// Check raw class match as well, making sure it's exposed on the proxy.
					// 也检查原生类型匹配，确保在代理上暴露
					Class<?> classToMatch = typeToMatch.resolve();
					if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
						// bean 实例类型与原生类型不匹配
						return false;
					}
					if (typeToMatch.isAssignableFrom(targetType)) {
						return true;
					}
				}
				// 目标类型
				ResolvableType resolvableType = mbd.targetType;
				if (resolvableType == null) {
					// 工厂方法返回类型
					resolvableType = mbd.factoryMethodReturnType;
				}
				// bean 定义的目标类型不为 null 且与给定类型匹配
				return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
			} else {
				return false;
			}
		} else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			// 注册了 null 实例
			return false;
		}

		// No singleton instance found -> check bean definition.
		// 未发现单例实例 -> 检查 bean 定义
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 当前工厂中未找到 bean 定义 -> 从父工厂查找
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		// 检索对应的 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		// 修饰的目标定义
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Set up the types that we want to match against
		// 要匹配的类型
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			// 为 null 则等于 FactoryBean
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[]{classToMatch} : new Class<?>[]{FactoryBean.class, classToMatch});

		// Attempt to predict the bean type
		// 尝试预测 bean 类型
		Class<?> predictedType = null;

		// We're looking for a regular reference, but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		// 正在查找常规引用，但实际是一个带修饰 bean 定义的工厂 bean。修饰的目标 bean 应该与 FactoryBean 最终返回的类型是同一类型
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			//应该仅在用户显式设置了懒加载标记为 true 且已知该工厂 bean 的合并 bean 定义时才做尝试
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				// 预测指定 bean 的最终 bean 类型
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		// 如果不能使用目标类型，则尝试常规预测
		if (predictedType == null) {
			// 预测指定 bean 的最终 bean 类型
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		// 尝试获得 bean 实际的解析类型
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		// 如果是一个 FactoryBean，我们想要知道它创建的是什么，而不是工厂类本身
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				// FactoryBean 创建的 bean 的实际类型
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		} else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type, but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			// 特殊情况：SmartInstantiationAwareBeanPostProcessor 返回一个非 FactoryBean 类型，但仍然要求我们间接引用一个 FactoryBean……
			// 如果是一个 FactoryBean，检查其原生 bean 类型并作处理
			// 预测指定 bean 的最终 bean 类型
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		// 没有确切的类型，但是如果 bean 定义目标类型或工厂方法返回类型与预测类型匹配，那么我们可以使用它。
		if (beanType == null) {
			// 目标类型
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				// 工厂方法返回类型
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				// 等于预测类型
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		// 如果有一个 bean 类型，请使用它，以便考虑泛型
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		// 如果没有 bean 类型，则回退到预测类型
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		// 手工检查已注册单例，不为 null 则返回的类型
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				// 返回对应 FactoryBean 管理的类型
				return getTypeForFactoryBean(factoryBean);
			} else {
				// 直接返回 bean 实例类型
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		// 未发现在单例实例 -> 检查 bean 定义
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 当前工厂未发现 bean 定义 -> 则从父类查找
			return parentBeanFactory.getType(originalBeanName(name));
		}

		// 获取合并的 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		// 预测指定 bean 的最终 bean 类型
		Class<?> beanClass = predictBeanType(beanName, mbd);

		if (beanClass != null) {
			// Check bean class whether we're dealing with a FactoryBean.
			// 是否是一个 FactoryBean
			if (FactoryBean.class.isAssignableFrom(beanClass)) {
				if (!BeanFactoryUtils.isFactoryDereference(name)) {
					// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
					// 如果是一个 FactoryBean，我们想要知道它创建的是什么，而不是工厂类本身
					beanClass = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
				}
			} else if (BeanFactoryUtils.isFactoryDereference(name)) {
				// 如果是间接引用，直接返回 null
				return null;
			}
		}

		if (beanClass == null) {
			// Check decorated bean definition, if any: We assume it'll be easier
			// to determine the decorated bean's type than the proxy's type.
			// 检查修饰的 bean 定义（如果有）：我们假设确定修饰的 bean 的类型比确定代理的类型更容易。
			BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
			if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				// 预测指定 bean 的最终 bean 类型
				Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
				if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
					return targetClass;
				}
			}
		}

		return beanClass;
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		// 是否是 FactoryBean
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			// fullBeanName 自己是一个别名
			aliases.add(fullBeanName);
		}
		// 获取别名列表
		String[] retrievedAliases = super.getAliases(beanName);
		// 如果是 FactoryBean，添加&前缀
		String prefix = (factoryPrefix ? FACTORY_BEAN_PREFIX : "");
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		// 不包含此单例，也不包含此 bean 定义，则从父工厂中获取别名
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface 实现自 HierarchicalBeanFactory 接口
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface  实现自 ConfigurableBeanFactory 接口
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 * 返回注册的属性编辑器注册表
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 * 返回自定义编辑器的映射，其中 Classes 作为键，PropertyEditor 类作为值。
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * Return the custom TypeConverter to use, if any.
	 * 返回要使用的自定义 TypeConverter（如果有）。
	 *
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		} else {
			// Build default TypeConverter, registering custom editors.
			// 创建默认的类型转换器 TypeConverter 并注册
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			// 先从原来的位置删除
			this.beanPostProcessors.remove(beanPostProcessor);
			// Add to end of list
			// 在尾部添加
			this.beanPostProcessors.add(beanPostProcessor);
		}
	}

	/**
	 * Add new BeanPostProcessors that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * 添加新的后置处理器 BeanPostProcessor，它将应用于此工厂创建的 bean。在工厂配置期间调用。
	 *
	 * @see #addBeanPostProcessor
	 * @since 5.3
	 */
	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			// 先从原来的位置删除
			this.beanPostProcessors.removeAll(beanPostProcessors);
			// Add to end of list
			// 在尾部添加
			this.beanPostProcessors.addAll(beanPostProcessors);
		}
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 * 返回将应用于使用此工厂创建的 bean 的后置处理器 BeanPostProcessors 的列表。
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * Return the internal cache of pre-filtered post-processors,
	 * freshly (re-)building it if necessary.
	 * 返回预过滤后置处理器的内部缓存，必要时重新构建它。
	 *
	 * @since 5.3
	 */
	BeanPostProcessorCache getBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			BeanPostProcessorCache bppCache = this.beanPostProcessorCache;
			if (bppCache == null) {
				// 为 null 时进行添加
				bppCache = new BeanPostProcessorCache();
				for (BeanPostProcessor bpp : this.beanPostProcessors) {
					if (bpp instanceof InstantiationAwareBeanPostProcessor instantiationAwareBpp) {
						bppCache.instantiationAware.add(instantiationAwareBpp);
						if (bpp instanceof SmartInstantiationAwareBeanPostProcessor smartInstantiationAwareBpp) {
							bppCache.smartInstantiationAware.add(smartInstantiationAwareBpp);
						}
					}
					if (bpp instanceof DestructionAwareBeanPostProcessor destructionAwareBpp) {
						bppCache.destructionAware.add(destructionAwareBpp);
					}
					if (bpp instanceof MergedBeanDefinitionPostProcessor mergedBeanDefBpp) {
						bppCache.mergedDefinition.add(mergedBeanDefBpp);
					}
				}
				this.beanPostProcessorCache = bppCache;
			}
			return bppCache;
		}
	}

	private void resetBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessorCache = null;
		}
	}

	/**
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 * 判断该工厂是否持有一个 InstantiationAwareBeanPostProcessor，以应用于在单例创建期间
	 *
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * 判断该工厂是否持有一个 DestructionAwareBeanPostProcessor，以应用于在单例关闭时
	 *
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		} else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory otherAbstractFactory) {
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
		} else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * 为给定 bean 名返回一个“合并的”bean 定义，在需要的时候，合并子 bean 定义和父 bean 定义。
	 * 该方法考虑了祖先中的 bean 定义。
	 *
	 * @param name the name of the bean to retrieve the merged definition for
	 *             (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException  in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);
		// Efficiently check whether bean definition exists in this factory.
		// 检查当前工厂是否存在指定的 bean 定义，不存在则向上查找父类
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory parent) {
			return parent.getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		// 本地解析合并 bean 定义
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
		// No singleton instance found -> check bean definition.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory cbf) {
			// No bean definition found in this factory -> delegate to parent.
			// 当前工厂中未找到 bean 定义 -> 从父工厂查找
			return cbf.isFactoryBean(name);
		}
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * Return whether the specified prototype bean is currently in creation
	 * (within the current thread).
	 * 在当前线程期间，指定的原型 bean 是否存在创建当中。
	 *
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set<?> set && set.contains(beanName))));
	}

	/**
	 * Callback before prototype creation.
	 * 原型 bean 创建前的回调方法。
	 * <p>The default implementation registers the prototype as currently in creation.
	 * 默认实现为在创建过程中将原型注册为当前正在创建的 bean，即将其放入 prototypesCurrentlyInCreation 中。
	 *
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		} else if (curVal instanceof String strValue) {
			Set<String> beanNameSet = CollectionUtils.newHashSet(2);
			beanNameSet.add(strValue);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		} else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * Callback after prototype creation.
	 * 原型 bean 创建后的回调方法。
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * 默认实现为将当前创建的 bean 标记为不在创建中，即将其从 prototypesCurrentlyInCreation 中删除。
	 *
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		} else if (curVal instanceof Set<?> beanNameSet) {
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		// 销毁此 bean
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * 根据给定的 bean 名和 bean 定义，销毁给定 bean 实例（通常是一个来自工厂的原型）。
	 *
	 * @param beanName the name of the bean definition
	 * @param bean     the bean instance to destroy
	 * @param mbd      the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		// 检索对应的 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		// 检索作用域
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		// 从作用域中删除此 bean
		Object bean = scope.remove(beanName);
		if (bean != null) {
			// 销毁此 bean
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods  已实现的、可被重写的方法，即 protected 方法
	//---------------------------------------------------------------------

	/**
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * 返回 bean 名称，必要时去除工厂间接引用前缀，并将别名解析为规范名称。
	 *
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		// 先去除 FactoryBean 前缀 &，再蒋别名替换为规范名
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * 确定原始 Bean 名称，将本地定义的别名解析为规范名称。
	 *
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		// 获得规范 bean 名
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			// 如果是 FactoryBean，则再添加一个 & 标记
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * 使用工厂中已注册的自定义编辑器初始化给定的 BeanWrapper 实例。用于创建和填充 bean 实例的 BeanWrapper 会被调用此方法。
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * 默认实现被代理到了 registerCustomEditors()　方法，可被子类型重写。
	 *
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
		registerCustomEditors(bw);
	}

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * 使用工厂中已注册的自定义编辑器初始化给定的 PropertyEditorRegistry 实例。
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * 用于创建和填充 bean 实例的 BeanWrapper 会被调用此方法，以及用于构造器参数和工厂方法类型转换的 SimpleTypeConverter 会被调用此方法。
	 *
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		if (registry instanceof PropertyEditorRegistrySupport registrySupport) {
			registrySupport.useConfigValueEditors();
		}
		if (!this.propertyEditorRegistrars.isEmpty()) {
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					// 注册
					registrar.registerCustomEditors(registry);
				} catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException bce) {
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 * 返回一个合并的 RootBeanDefinition，如果指定的 bean 与子 bean 定义相对应，则遍历其父 bean 定义。
	 *
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException  in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		// 首先快速检查并发映射集合，尽量减少锁定。
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			// 存在且不需要重新合并，则直接返回
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * 为给定的顶级 bean 返回一个 RootBeanDefinition bean 定义，如果给定的 bean 定义是一个子 bean 定义，则与父类合并。
	 *
	 * @param beanName the name of the bean definition
	 * @param bd       the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 * 为给定的 bean 返回一个 RootBeanDefinition bean 定义，如果给定的 bean 定义是一个子 bean 定义，则与父类合并。
	 *
	 * @param beanName     the name of the bean definition
	 * @param bd           the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 *                     or {@code null} in case of a top-level bean
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {
		// 在合并的 bean 定义集合上加锁
		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous = null;

			// Check with full lock now in order to enforce the same merged instance.
			// 完全锁检查，确保是同一合并实例
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}
			// bean 定义为 null 或需要进行合并
			if (mbd == null || mbd.stale) {
				previous = mbd;
				// 不存在父 bean 定义
				if (bd.getParentName() == null) {
					// Use copy of given root bean definition.
					// 确保是 RootBeanDefinition 类型
					if (bd instanceof RootBeanDefinition rootBeanDef) {
						mbd = rootBeanDef.cloneBeanDefinition();
					} else {
						mbd = new RootBeanDefinition(bd);
					}
				} else {
					// Child bean definition: needs to be merged with parent.
					// 子 bean 定义：需要和父 bean 定义合并
					BeanDefinition pbd;
					try {
						// 获得父级规范 bean 名
						String parentBeanName = transformedBeanName(bd.getParentName());
						if (!beanName.equals(parentBeanName)) {
							// 合并
							pbd = getMergedBeanDefinition(parentBeanName);
						} else {
							if (getParentBeanFactory() instanceof ConfigurableBeanFactory parent) {
								// 从低级开始合并
								pbd = parent.getMergedBeanDefinition(parentBeanName);
							} else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
												"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					} catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					// 深拷贝，改写值，即重新创建了一个新的 bean 定义
					mbd = new RootBeanDefinition(pbd);
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				// 设置为默认单例作用域，如果之前未配置的话。
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				// 一个 bean 如果包含了一个非单例的 bean，则其本身不能是一个单例 bean。在这里即时更正此问题，因为这可能是外部 bean 的
				// 父子合并的结果，在这种情况下，原始内部 bean 定义将不会继承合并的外部 bean 的单例状态。
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				// 暂时缓存合并后的 bean 定义（它稍后可能仍会重新合并，以便获取元数据变化）
				if (containingBd == null && (isCacheBeanMetadata() || isBeanEligibleForMetadataCaching(beanName))) {
					// 缓存
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			if (previous != null) {
				// 将之前缓存的 bean 定义的一些属性拷贝到新的缓存 bean 定义中
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			return mbd;
		}
	}

	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			// 拷贝某些属性到新的 bean 定义中
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
			if (previous.hasMethodOverrides()) {
				mbd.setMethodOverrides(new MethodOverrides(previous.getMethodOverrides()));
			}
		}
	}

	/**
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * 检查给定的合并 bean 定义是否是抽象的，如果是，则抛出 bean 为抽象异常。
	 *
	 * @param mbd      the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args     the arguments for bean creation, if any
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args) {
		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * 删除指定 bean 的合并 bean 定义，下次访问时重新生成。
	 *
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * 清理合并的 bean 定义缓存，删除尚未被视为符合完整元数据缓存条件的 Bean 的条目。
	 * <p>Typically triggered after changes to the original bean definitions,
	 * for example, after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * 通常在改变了原始 bean 定义数据后触发此方法，如，在应用了后置处理器 BeanFactoryPostProcessor 后。
	 * 注意，此时已经创建的 bean 的元数据将被保留。
	 *
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 * 解析指定 Bean 定义的 Bean 类，将 Bean 类名解析为类引用（如有必要），并将解析的类存储在 Bean 定义中以供进一步使用。
	 *
	 * @param mbd          the merged bean definition to determine the class for
	 * @param beanName     the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 *                     (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			// 解析 bean 类型
			Class<?> beanClass = doResolveBeanClass(mbd, typesToMatch);
			if (mbd.hasBeanClass()) {
				mbd.prepareMethodOverrides();
			}
			return beanClass;
		} catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		} catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		} catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}
	}

	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {
		// 获取类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		// 动态类加载器
		ClassLoader dynamicLoader = beanClassLoader;
		boolean freshResolve = false;

		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (for example, in a weaving scenario).
			// 只做类型检查时（如不去创建实际的实例），使用指定的临时类加载器（如在一个织入场景中）。
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				dynamicLoader = tempClassLoader;
				freshResolve = true;
				if (tempClassLoader instanceof DecoratingClassLoader dcl) {
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		// 获取 bean 类型名
		String className = mbd.getBeanClassName();
		if (className != null) {
			// 对类型名作表达式计算
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			// 如果类型名与计算后的值不等（说明类型名中存在表达式）
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2...
				// 一个动态解析的表达式，从 4.2 开始支持……
				if (evaluated instanceof Class<?> clazz) {
					// 计算得到的是一个类型，则直接返回
					return clazz;
				} else if (evaluated instanceof String name) {
					// 计算得到的是一个字符串，则将其当作类名，继续处理
					className = name;
					freshResolve = true;
				} else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				// 针对临时类加载器进行解析时，请提前退出，以避免将解析的 Class 存储在 Bean 定义中。
				if (dynamicLoader != null) {
					try {
						return dynamicLoader.loadClass(className);
					} catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition...
		// 常规解析，并将结果缓存在 bean 定义中
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * 计算 Bean 定义中包含的给定 String，并可能将其解析为表达式。
	 *
	 * @param value          the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		if (this.beanExpressionResolver == null) {
			return value;
		}

		Scope scope = null;
		if (beanDefinition != null) {
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}
		// 表达式计算
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}

	/**
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * 预测指定 bean 的最终 bean 类型（已处理的 Bean 实例），由 getType() 和 isTypeMatch() 调用。
	 * 不需要专门处理 FactoryBean，因为它只应该对原始 bean 类型进行操作。
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * 这个实现很简单，因为它不能处理工厂方法和InstantiationAwareBeanPostProcessors。
	 * 它只能正确预测标准 bean 的 bean 类型。在子类中被覆盖，以应用更复杂的类型检测。
	 *
	 * @param beanName     the name of the bean
	 * @param mbd          the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 *                     (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 获取 bean 定义的目标类型并返回
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
		// 工厂方法名不为 null 时，返回 null
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		// 解析 bean 类型
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * 判断给定的 bean 是否定义为一个 FactoryBean。
	 *
	 * @param beanName the name of the bean
	 * @param mbd      the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			// 预测指定 bean 的最终 bean 类型
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			// 根据 bean 类型判断是否为工厂 bean
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * 尽可能确定给定 FactoryBean 定义的 bean 类型。仅当尚未为目标 Bean 注册单例实例时调用。
	 * 如果 allowInit 属性为 true 且类型不能通过其他方式识别时，该实现允许初始化该目标工厂 bean，
	 * 否则，它仅限于内省签名和相关元数据。
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} is set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * 如果在 bean 定义中未设置 OBJECT_TYPE_ATTRIBUTE 属性，且 allowInit 设置为 true，则默认实现会通过 getBean() 创建对应的 FactoryBean
	 * 以调用它的 getObjectType() 方法。鼓励子类对此进行优化，通常做法是检查这个工厂 bean 类的泛型签名或创建它的工厂方法。如果子类初始化了这个
	 * FactoryBean，则它们应该考虑在不完全填充 bean 的情况下尝试 getObjectType() 方法。如果此操作失败，则应使用此实现执行的完整 FactoryBean 创建作为后备。
	 *
	 * @param beanName  the name of the bean
	 * @param mbd       the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 *                  cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @since 5.2
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		try {
			// 通过检查 FactoryBean 的属性中的 OBJECT_TYPE_ATTRIBUTE 值来确定 FactoryBean 的 Bean 类型。
			ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
			if (result != ResolvableType.NONE) {
				return result;
			}
		} catch (IllegalArgumentException ex) {
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
					String.valueOf(ex.getMessage()));
		}

		// 允许初始化并且是单例
		if (allowInit && mbd.isSingleton()) {
			try {
				// 创建并获取 FactoryBean
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				// 判断给定 FactoryBean 所创建的 bean 的类型
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				// 返回 ResolvableType 类型
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			} catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				} else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				} else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}

		// FactoryBean type not resolvable
		// FactoryBean 创建的 bean 的类型未解析
		return ResolvableType.NONE;
	}

	/**
	 * Mark the specified bean as already created (or about to be created).
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean.
	 * 标记指定的 bean 为已创建（或即将创建）。对于重复创建 bean 时，这将允许 bean 工整优化其缓存。
	 *
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				// synchronized 的双重判断，与上面一行的 if 判断是一样的
				if (!isBeanEligibleForMetadataCaching(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					// 目前正在创建 bean，重新合并一下 bean 定义，以防它的一些元数据在此期间发生了变化。
					clearMergedBeanDefinition(beanName);
				}
				this.alreadyCreated.add(beanName);
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * 在 bean 创建失败后，执行适当的无数据缓存的清理工作。
	 *
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * 判断指定的 bean 是否有资格缓存其 Bean 定义元数据，即判断该 bean 是否已被标记为已创建。
	 *
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * 删除给定单例实例（如果有的话），但前提是它未用于类型检查以外的其他目的。
	 *
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * 检查此工厂的 bean 创建阶段是否已经开始，即在此期间是否有任何 bean 被标记为已创建。
	 *
	 * @see #markBeanAsCreated
	 * @since 4.2.2
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * 获取给定 bean 实例的对象，可以是 bean 实例本身，也可以是 FactoryBean 创建的对象。
	 *
	 * @param beanInstance the shared bean instance
	 * @param name         the name that may include factory dereference prefix
	 * @param beanName     the canonical bean name
	 * @param mbd          the merged bean definition
	 * @return the object to expose for the bean
	 */
	protected Object getObjectForBeanInstance(Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {
		// Don't let calling code try to dereference the factory if the bean isn't a factory.
		// 如果 bean 不是工厂，请不要让调用代码尝试间接引用工厂。
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		// 现在我们有了 bean 实例，可能是正常的 bean，也可能是一个 FactoryBean。如果是一个 FactoryBean，则用它
		// 创建一个 bean 实例，除非调用者实际上只是想获取一个该工厂的引用。
		if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
			// 不是工厂 bean 直接返回
			return beanInstance;
		}

		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		} else {
			// 获取 FactoryBean 创建的单例
			object = getCachedObjectForFactoryBean(beanName);
		}
		if (object == null) {
			// Return bean instance from factory.
			// 从工厂中返回 bean 实例
			// Caches object obtained from FactoryBean if it is a singleton.
			// 如果从 FactoryBean 获取的对象是单例，则缓存该对象。
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			// 是否为合并的 bean 定义
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			// 从 FactoryBean 获取 bean，没有则返回一个 NullBean
			object = getObjectFromFactoryBean(factoryBean, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * 确定给定的 bean 名称是否已在此工厂中使用，即是否有在此名称下注册的本地 bean 或别名，或者是否使用此名称创建的内部 bean。
	 *
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		// 是一个别名，或当前工厂包含此 bean，或是一个依赖 bean
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * 确定给定的 bean 在关闭时是否需要被销毁。
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * 默认实现为检查 DisposableBean 接口和指定的销毁方法及注册的 DestructionAwareBeanPostProcessors 处理器。
	 *
	 * @param bean the bean instance to check
	 * @param mbd  the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		// 非 NullBean 且有销毁方法，或当前工厂拥有 DestructionAwareBeanPostProcessors 处理器且指定 bean 应用了可识别销毁的后处理器
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * 将给定的 bean 添加到此工厂中的一次性 bean 列表中，注册其 DisposableBean 接口和/或要在工厂关闭时调用的给定的 destroy 方法（如果适用）。
	 * 仅适用于单例。
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance
	 * @param mbd      the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			} else {
				// A bean with a custom scope...
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses  需要被子类实现的抽象方法
	//---------------------------------------------------------------------

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * 检查 bean 工厂是否已包含给定名称的 bean 定义。
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * 在未发现缓存的单例实例时，被 containsBean() 调用。
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * 据具体 Bean 工厂实现的性质，此操作可能很昂贵（例如，由于外部注册表中的目录查找）。但是，对于可列出的 bean 工厂，这通常只相当于
	 * 本地哈希查找：因此，该操作是公共接口的一部分。在这种情况下，相同的实现可以同时用于此模板方法和公共接口方法。
	 *
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * 返回给定 bean 名称的 bean 定义。子类通常应该实现缓存，因为每次需要 bean 定义元数据时，该类都会调用此方法。
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * 据具体 Bean 工厂实现的性质，此操作可能很昂贵（例如，由于外部注册表中的目录查找）。但是，对于可列出的 bean 工厂，这通常只相当于
	 * 本地哈希查找：因此，该操作是公共接口的一部分。在这种情况下，相同的实现可以同时用于此模板方法和公共接口方法。
	 *
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if the bean definition cannot be resolved
	 * @throws BeansException                                                  in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * 为给定的合并 bean 定义（和其他参数）创建一个 bean 实例。如果是子 bean 定义，则其已经与父定义合并。
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * 所有检索方法都会代理到本方法，以完成实际的 bean 创建。
	 *
	 * @param beanName the name of the bean
	 * @param mbd      the merged bean definition for the bean
	 * @param args     explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;

	/**
	 * CopyOnWriteArrayList which resets the beanPostProcessorCache field on modification.
	 * CopyOnWriteArrayList，它会在修改时重置 beanPostProcessorCache。
	 *
	 * @since 5.3
	 */
	@SuppressWarnings("serial")
	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			resetBeanPostProcessorCache();
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			resetBeanPostProcessorCache();
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			resetBeanPostProcessorCache();
		}
	}

	/**
	 * Internal cache of pre-filtered post-processors.
	 * 预过滤的后置处理器的内部缓存容器。
	 *
	 * @since 5.3
	 */
	static class BeanPostProcessorCache {

		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}

}
