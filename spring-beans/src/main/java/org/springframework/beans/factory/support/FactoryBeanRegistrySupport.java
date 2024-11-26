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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 * 支持单例注册表的基类，这些注册表需要处理 FactoryBean 实例，并与 DefaultSingletonBeanRegistry 的单例管理集成。
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 * 作为 AbstractBeanFactory 的基类使用。
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/**
	 * Cache of singleton objects created by FactoryBeans: FactoryBean name to object.
	 * FactoryBean 创建的单例对象缓存：<FactoryBean 名字，单例>
	 */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * Determine the type for the given FactoryBean.
	 * 判断给定 FactoryBean 所创建的 bean 的类型。
	 *
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	@Nullable
	protected Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			return factoryBean.getObjectType();
		} catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * 通过检查 FactoryBean 的属性中的 OBJECT_TYPE_ATTRIBUTE 值来确定 FactoryBean 的 Bean 类型。
	 *
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute == null) {
			return ResolvableType.NONE;
		}
		if (attribute instanceof ResolvableType resolvableType) {
			return resolvableType;
		}
		if (attribute instanceof Class<?> clazz) {
			return ResolvableType.forClass(clazz);
		}
		throw new IllegalArgumentException("Invalid value type for attribute '" +
				FactoryBean.OBJECT_TYPE_ATTRIBUTE + "': " + attribute.getClass().getName());
	}

	/**
	 * Determine the FactoryBean object type from the given generic declaration.
	 * 根据给定泛型声明，判断 FactoryBean 对象类型。
	 *
	 * @param type the FactoryBean type
	 * @return the nested object type, or {@code NONE} if not resolvable
	 */
	ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		return (type != null ? type.as(FactoryBean.class).getGeneric() : ResolvableType.NONE);
	}

	/**
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
	 * 如果以缓存形式提供的话，从给定的 FactoryBean 获得其暴露出来的 bean。快速检查最小同步（因为是 ConcurrentHashMap 集合？）。
	 *
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * 从给定的 FactoryBean 获取其要暴露出去的 bean 对象。
	 *
	 * @param factory           the FactoryBean instance
	 * @param beanName          the name of the bean
	 * @param shouldPostProcess whether the bean is subject to post-processing 不是合成 bean 时为 true
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		// 是单例，且在单例缓存中
		if (factory.isSingleton() && containsSingleton(beanName)) {
			Object object = this.factoryBeanObjectCache.get(beanName);
			// 不存在则创建，并放入缓存
			if (object == null) {
				// 从 FactoryBean 获取 bean
				object = doGetObjectFromFactoryBean(factory, beanName);
				// Only post-process and store if not put there already during getObject() call above
				// (for example, because of circular reference processing triggered by custom getBean calls)
				// 如果在上面的 getObject() 调用期间没有缓存在 factoryBeanObjectCache，则只进行后置处理和存储（例如，由于自定义 getBean 调用触发了循环引用处理）
				Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
				if (alreadyThere != null) {
					// 如果在这期间已经生成了 bean
					object = alreadyThere;
				} else {
					if (shouldPostProcess) {
						if (isSingletonCurrentlyInCreation(beanName)) {
							// Temporarily return non-post-processed object, not storing it yet
							// 如果 bean 还在创建当中，临时返回一个未做后置处理的对象，且先不缓存它
							return object;
						}
						// 标记该 bean 名为正在创建当中
						beforeSingletonCreation(beanName);
						try {
							// 对 FactoryBean 中的 bean 作后置处理
							object = postProcessObjectFromFactoryBean(object, beanName);
						} catch (Throwable ex) {
							throw new BeanCreationException(beanName,
									"Post-processing of FactoryBean's singleton object failed", ex);
						} finally {
							// 标记该 bean 名为已创建结束
							afterSingletonCreation(beanName);
						}
					}
					// 如果已在单例缓存中
					if (containsSingleton(beanName)) {
						// 放入 FactoryBean 的单例对象缓存中
						this.factoryBeanObjectCache.put(beanName, object);
					}
				}
			}
			return object;
		} else {
			// 不是单例，且不在单例缓存中
			// 从 FactoryBean 获取 bean
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (shouldPostProcess) {
				try {
					// 对 FactoryBean 中的 bean 作后置处理
					object = postProcessObjectFromFactoryBean(object, beanName);
				} catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * 从给定的 FactoryBean 获取要暴露出去的对象。
	 *
	 * @param factory  the FactoryBean instance
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, String beanName) throws BeanCreationException {
		Object object;
		try {
			// 从 FactoryBean 获取 bean
			object = factory.getObject();
		} catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		} catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		// 不要为尚未完全初始化的 FactoryBean 接受 null 值：许多 FactoryBean 只返回 null。要返回一个 NullBean 对象。
		if (object == null) {
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			object = new NullBean();
		}
		return object;
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * 对从 FactoryBean 获取的给定对象进行后置处理。
	 * The resulting object will get exposed for bean references.
	 * 结果对象将暴露给 bean 引用。
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * 默认实现只是按原样返回给定的对象。子类可以覆盖它，例如，对其应用后置处理器。
	 *
	 * @param object   the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * 如果可能，获取给定 bean 的 FactoryBean。
	 *
	 * @param beanName     the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return factoryBean;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 * 重写，清理 FactoryBean 对象缓存，清理指定的 bean 缓存。
	 */
	@Override
	protected void removeSingleton(String beanName) {
		// 向上调用父类的相同方法
		super.removeSingleton(beanName);
		// 从 factoryBeanObjectCache 集合中删除
		this.factoryBeanObjectCache.remove(beanName);
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 * 重写，清理 FactoryBean 对象缓存，清理所有单例缓存。
	 */
	@Override
	protected void clearSingletonCache() {
		// 向上调用父类的相同方法
		super.clearSingletonCache();
		// 清空 factoryBeanObjectCache 集合
		this.factoryBeanObjectCache.clear();
	}

}
