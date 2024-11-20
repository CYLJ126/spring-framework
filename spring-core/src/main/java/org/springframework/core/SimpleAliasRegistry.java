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

package org.springframework.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple implementation of the {@link AliasRegistry} interface.
 * AliasRegistry 接口的简单实现。
 *
 * <p>Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 * 作为 BeanDefinitionRegistry 接口实现类的基类使用。
 *
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @author Sam Brannen
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/**
	 * Logger available to subclasses.
	 * 可用于子类的 Logger。
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Map from alias to canonical name.
	 * 别名到规范名的映射集合。
	 */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

	/**
	 * List of alias names, in registration order.
	 * 按注册顺序存放的别名列表。
	 */
	private final List<String> aliasNames = new ArrayList<>(16);


	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		// 对 aliasMap 使用 synchronized 同步
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				// 传入的 bean 名与别名相同，则从别名集合中删除
				this.aliasMap.remove(alias);
				this.aliasNames.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			} else {
				// 传入的 bean 名与别名不同
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					// 如果已存在别名到 bean 名的映射，则什么也不做
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				// 检查是否存在 bean 名 -> 别名 -> bean 名的循环引用，存在则会抛出异常
				checkForAliasCircle(name, alias);
				// 设置别名
				this.aliasMap.put(alias, name);
				this.aliasNames.add(alias);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * Determine whether alias overriding is allowed.
	 * 判断是否允许重写（覆盖）别名，默认为 true。
	 * <p>Default is {@code true}.
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * Determine whether the given name has the given alias registered.
	 * 判断给定 bean 名是否已经注册了给定别名。
	 *
	 * @param name  the name to check
	 * @param alias the alias to look for
	 * @since 4.2.1
	 */
	public boolean hasAlias(String name, String alias) {
		String registeredName = this.aliasMap.get(alias);
		return ObjectUtils.nullSafeEquals(registeredName, name) ||
				(registeredName != null && hasAlias(name, registeredName));
	}

	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			this.aliasNames.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}

	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Transitively retrieve all aliases for the given name.
	 * 传递式检索给定名称的所有别名，即别名的别名也放到结果列表中返回。
	 *
	 * @param name   the target name to find aliases for
	 * @param result the resulting aliases list
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				// 递归调用自己，取别名的别名
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * Resolve all alias target names and aliases registered in this
	 * registry, applying the given {@link StringValueResolver} to them.
	 * 使用给定的 StringValueResolver 解析器解析此注册表中注册的所有别名目标名称和别名。
	 *
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * 例如，该值解析器可以解析目标 bean 名称甚至别名中的占位符。
	 *
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		// 对 aliasMap 使用 synchronized 同步
		synchronized (this.aliasMap) {
			List<String> aliasNamesCopy = new ArrayList<>(this.aliasNames);
			// 遍历别名列表
			aliasNamesCopy.forEach(alias -> {
				// 拿到别名对应的目标名称
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					// 解析别名
					String resolvedAlias = valueResolver.resolveStringValue(alias);
					// 解析目标名
					String resolvedName = valueResolver.resolveStringValue(registeredName);
					if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
						// 如果解析后的别名为 null，或解析后的目标名为 null，或解析后的别名等于解析后的目标名，则从别名列表中删除该别名（因为没必要存）
						this.aliasMap.remove(alias);
						this.aliasNames.remove(alias);
					} else if (!resolvedAlias.equals(alias)) {
						// 解析后的别名与解析后的目标名不相等
						// 通过解析后的别名获取对应的目标名
						String existingName = this.aliasMap.get(resolvedAlias);
						if (existingName != null) {
							if (existingName.equals(resolvedName)) {
								// Pointing to existing alias - just remove placeholder
								// 如果解析后的别名对应的目标名与解析前的别名对应的目标名相同，则删除（因为没必要存）
								this.aliasMap.remove(alias);
								this.aliasNames.remove(alias);
								return;
							}
							// 如果不相等，则抛出非法异常 IllegalStateException
							throw new IllegalStateException(
									"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
											"') for name '" + resolvedName + "': It is already registered for name '" +
											existingName + "'.");
						}
						// 检查是否存在 bean 名 -> 别名 -> bean 名的循环引用，存在则会抛出异常
						checkForAliasCircle(resolvedName, resolvedAlias);
						// 删除原别名条目
						this.aliasMap.remove(alias);
						this.aliasNames.remove(alias);
						// 放入解析后的别名条目
						this.aliasMap.put(resolvedAlias, resolvedName);
						this.aliasNames.add(resolvedAlias);
					} else if (!registeredName.equals(resolvedName)) {
						// 如果解析前的目标名和解析后的目标名不相等，则放入解析后的目标名
						this.aliasMap.put(alias, resolvedName);
						this.aliasNames.add(alias);
					}
				}
			});
		}
	}

	/**
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 * 检查给定的名称是否已经指向给定的别名作为另一个方向的别名，预先捕获循环引用并抛出相应的 IllegalStateException。
	 * 意即存在 bean 名 -> 别名 -> bean 名的循环引用。
	 *
	 * @param name  the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		// 判断给定 bean 名是否已经注册了给定别名，是则抛出非法异常 IllegalStateException。
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * Determine the raw name, resolving aliases to canonical names.
	 * 确定原始名称，将别名解析为规范名称。即从别名-规范名的 Map 中反复迭代，找到最终的规范名。
	 *
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		// 循环调用，找到最终的 bean 名（可能别名也存在别名，存在一条别名链）
		while (resolvedName != null);
		return canonicalName;
	}

}
