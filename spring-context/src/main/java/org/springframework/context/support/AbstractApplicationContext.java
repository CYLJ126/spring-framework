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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.*;
import org.springframework.context.event.*;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.NativeDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * for example, "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @author Brian Clozel
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 * @since January 21, 2001
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * The name of the {@link MessageSource} bean in the context.
	 * If none is supplied, message resolution is delegated to the parent.
	 * 上下文中 MessageSource bean 的名字，如果未提供，则消息解析将委托给父类。
	 *
	 * @see org.springframework.context.MessageSource
	 * @see org.springframework.context.support.ResourceBundleMessageSource
	 * @see org.springframework.context.support.ReloadableResourceBundleMessageSource
	 * @see #getMessage(MessageSourceResolvable, Locale)
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * The name of the {@link ApplicationEventMulticaster} bean in the context.
	 * If none is supplied, a {@link SimpleApplicationEventMulticaster} is used.
	 * 上下文中 ApplicationEventMulticaster bean 的名字，如果未提供，则默认使用 SimpleApplicationEventMulticaster。
	 *
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 * @see #publishEvent(ApplicationEvent)
	 * @see #addApplicationListener(ApplicationListener)
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";

	/**
	 * The name of the {@link LifecycleProcessor} bean in the context.
	 * If none is supplied, a {@link DefaultLifecycleProcessor} is used.
	 * 上下文中 LifecycleProcessor 的 bean 名。如果未提供，则使用 DefaultLifecycleProcessor。
	 *
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 * @see #start()
	 * @see #stop()
	 * @since 3.0
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";


	static {
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		ContextClosedEvent.class.getName();
	}


	/**
	 * Logger used by this class. Available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Unique id for this context, if any.
	 */
	private String id = ObjectUtils.identityToString(this);

	/**
	 * Display name.
	 */
	private String displayName = ObjectUtils.identityToString(this);

	/**
	 * Parent context.
	 */
	@Nullable
	private ApplicationContext parent;

	/**
	 * Environment used by this context.
	 */
	@Nullable
	private ConfigurableEnvironment environment;

	/**
	 * BeanFactoryPostProcessors to apply on refresh.
	 */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/**
	 * System time in milliseconds when this context started.
	 */
	private long startupDate;

	/**
	 * Flag that indicates whether this context is currently active.
	 */
	private final AtomicBoolean active = new AtomicBoolean();

	/**
	 * Flag that indicates whether this context has been closed already.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Synchronization lock for "refresh" and "close".
	 */
	private final Lock startupShutdownLock = new ReentrantLock();

	/**
	 * Currently active startup/shutdown thread.
	 */
	@Nullable
	private volatile Thread startupShutdownThread;

	/**
	 * Reference to the JVM shutdown hook, if registered.
	 */
	@Nullable
	private Thread shutdownHook;

	/**
	 * ResourcePatternResolver used by this context.
	 */
	private final ResourcePatternResolver resourcePatternResolver;

	/**
	 * LifecycleProcessor for managing the lifecycle of beans within this context.
	 */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/**
	 * MessageSource we delegate our implementation of this interface to.
	 */
	@Nullable
	private MessageSource messageSource;

	/**
	 * Helper class used in event publishing.
	 */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/**
	 * Application startup metrics.
	 */
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/**
	 * Statically specified listeners.
	 */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/**
	 * Local listeners registered before refresh.
	 */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/**
	 * ApplicationEvents published before the multicaster setup.
	 */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 */
	public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 *
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 *
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 *
	 * @return a display name for this context (never {@code null})
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 *
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 *
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * Publish the given event to all listeners.
	 * 向所有监听器发布给定事件。
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * 监听器在 MessageSource 之后被初始化，以便能够在侦听器实现中访问它。因此，MessageSource 的实现不能发布事件。
	 *
	 * @param event the event to publish (may be application-specific or a
	 *              standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 *
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 *              or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>This is the internal delegate that all other {@code publishEvent}
	 * methods refer to. It is not meant to be called directly but rather serves
	 * as a propagation mechanism between application contexts in a hierarchy,
	 * potentially overridden in subclasses for a custom propagation arrangement.
	 *
	 * @param event    the event to publish (may be an {@link ApplicationEvent}
	 *                 or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param typeHint the resolved event type, if known.
	 *                 The implementation of this method also tolerates a payload type hint for
	 *                 a payload object to be turned into a {@link PayloadApplicationEvent}.
	 *                 However, the recommended way is to construct an actual event object via
	 *                 {@link PayloadApplicationEvent#PayloadApplicationEvent(Object, Object, ResolvableType)}
	 *                 instead for such scenarios.
	 * @see ApplicationEventMulticaster#multicastEvent(ApplicationEvent, ResolvableType)
	 * @since 4.2
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType typeHint) {
		Assert.notNull(event, "Event must not be null");
		ResolvableType eventType = null;

		// Decorate event as an ApplicationEvent if necessary
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent applEvent) {
			applicationEvent = applEvent;
			eventType = typeHint;
		} else {
			ResolvableType payloadType = null;
			if (typeHint != null && ApplicationEvent.class.isAssignableFrom(typeHint.toClass())) {
				eventType = typeHint;
			} else {
				payloadType = typeHint;
			}
			applicationEvent = new PayloadApplicationEvent<>(this, event, payloadType);
		}

		// Determine event type only once (for multicast and parent publish)
		if (eventType == null) {
			eventType = ResolvableType.forInstance(applicationEvent);
			if (typeHint == null) {
				typeHint = eventType;
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		} else if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.multicastEvent(applicationEvent, eventType);
		}

		// Publish event via parent context as well...
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext abstractApplicationContext) {
				abstractApplicationContext.publishEvent(event, typeHint);
			} else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 *
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
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

	/**
	 * Return the internal LifecycleProcessor used by the context.
	 *
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 *
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 *
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment configurableEnvironment) {
				getEnvironment().merge(configurableEnvironment);
			}
		}
	}

	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	@Override
	public void removeApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.removeApplicationListener(listener);
		}
		this.applicationListeners.remove(listener);
	}

	/**
	 * Return the list of statically specified ApplicationListeners.
	 * 返回静态指定的应用监听器列表。
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	@Override
	public void refresh() throws BeansException, IllegalStateException {
		// 加锁
		this.startupShutdownLock.lock();
		try {
			this.startupShutdownThread = Thread.currentThread();

			// 初始化启动步骤记录
			StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");

			// Prepare this context for refreshing.
			// 作一些刷新前的准备，设置启动时间、启动标记，执行一些配置资源（property sources）的初始化动作
			// 通过 Environment 校验必要配置是否可解析、初始化earlyApplicationListeners
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
			// 让子类刷新内部 bean 工厂并返回，内部调用了refreshBeanFactory()去刷新，并调用getBeanFactory()返回，两个都是抽象方法
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
			// 准备该上下文使用的bean工厂，调用beanFactory参数的相关方法作一些设置
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
				// 允许子类上下文对 bean 工厂作后置处理，即子类自己注册需要的 BeanPostProcessor，默认实现为空
				postProcessBeanFactory(beanFactory);

				//定义新步骤记录
				StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");
				// Invoke factory processors registered as beans in the context.
				// 调用在上下文中注册为 bean 的工厂处理器，最终调用 BeanFactoryPostProcessor.postProcessBeanFactory()
				invokeBeanFactoryPostProcessors(beanFactory);
				// Register bean processors that intercept bean creation.
				// 注册拦截bean创建的bean处理器，即初始化并注册所有的BeanPostProcessor bean
				registerBeanPostProcessors(beanFactory);
				//beanPostProcess步骤结束
				beanPostProcess.end();

				// Initialize message source for this context.
				// 初始化 MessageSource
				initMessageSource();

				// Initialize event multicaster for this context.
				// 初始化组播器 ApplicationEventMulticaster
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				// 初始化子类特殊上下文中的其他特殊bean，默认实现为空
				onRefresh();

				// Check for listener beans and register them.
				// 检查监听器 bean 并注册，即向组播器中添加监听器，并将日期的应用事件组播出去
				registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
				// 初始化剩余单例（非懒加载的），完成工厂的初始化
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				// 最后一步：发布对应的事件，结束该上下文的刷新。
				finishRefresh();
			} catch (RuntimeException | Error ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				// 销毁已创建的单例 bean 以避免悬挂资源。
				destroyBeans();

				// Reset 'active' flag.
				// 重置 active 标记，将其设置为 false
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			} finally {
				contextRefresh.end();
			}
		} finally {
			this.startupShutdownThread = null;
			this.startupShutdownLock.unlock();
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 * 作一些刷新前的准备，
	 * 1. 设置启动时间、active标记；
	 * 2. 执行一些配置资源（property sources）的初始化动作；
	 * 3. 通过 Environment 校验必要配置是否可解析；
	 * 4. 初始化earlyApplicationListeners
	 */
	protected void prepareRefresh() {
		// Switch to active.
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			} else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// Initialize any placeholder property sources in the context environment.
		// 初始化上下文环境中的占位符属性资源（用真实的值替代存根属性资源)，默认实现为空
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		// 通过 Environment 校验必要配置是否可解析：见ConfigurablePropertyResolver
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
		// 初始化earlyApplicationListeners
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		} else {
			// Reset local application listeners to pre-refresh state.
			// 重置本地应用监听器为刷新前状态
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		// 当组播器可用时，允许早期应用事件发布……
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 * 用真实的值替代存根属性资源。
	 *
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * Tell the subclass to refresh the internal bean factory.
	 * 让子类刷新内部 bean 工厂
	 *
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		// 抽象方法，子类实现
		refreshBeanFactory();
		// 抽象方法，子类实现
		return getBeanFactory();
	}

	/**
	 * Configure the factory's standard context characteristics,
	 * such as the context's ClassLoader and post-processors.
	 * 配置工厂的标准上下文特性，如上下文的类加载器和后置处理器。
	 * 1. 设置类加载器，用于加载类路径资源
	 * 2. 设置 SpEL 表达式解析器
	 * 3. 添加 PropertyEditorRegistrar，用于注册 PropertyEditor；
	 * 4. 添加 ApplicationContextAwareProcessor 后置处理器；
	 * 5. 忽略 EnvironmentAware 等依赖接口；
	 * 6. 注册依赖接口 BeanFactory、ResourceLoader、ApplicationEventPublisher、ApplicationContext；
	 * 7. 注册 ApplicationListenerDetector，用于检测实现了 ApplicationListener 接口的 bean；
	 * 8. 注册 LoadTimeWeaver，并设置一个临时类加载器，用于类型匹配；
	 * 9. 注册 Environment、系统属性、环境变量、ApplicationStartup；
	 *
	 * @param beanFactory the BeanFactory to configure
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		// 设置类加载器，用于加载类路径资源
		beanFactory.setBeanClassLoader(getClassLoader());
		// 设置 SpEL 表达式解析器
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		// 为 bean 的创建过程，添加一个 PropertyEditorRegistrar，用于注册 PropertyEditor
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		// 使用上下文回调配置 Bean 工厂。
		// 设置 bean 后置处理器
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		// 忽略以下 Aware 接口（在依赖检查和自动注入时需要忽略的依赖接口集合），表示这些 Aware 接口将会以其它方式注册
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		// 在原生工厂中，BeanFactory 不会被注册为已解析的类型。MessageSource 注册（和自动注入时发现）为一个 bean。
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		// 注册早期后置处理器，用于检测实现了 ApplicationListener 接口的 bean
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		// 检测 LoadTimeWeaver，如果找到的话，准备织入。
		if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			// 设置一个临时类加载器，用于类型匹配。
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		// 注册默认的 Environment bean，bean 名"environment"
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			// 系统属性注册，bean 名"systemProperties"
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			// 环境变量注册，bean 名"systemEnvironment"
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
		if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
			// ApplicationStartup 注册，bean 名"applicationStartup"
			beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. The initial definition resources will have been loaded but no
	 * post-processors will have run and no derived bean definitions will have been
	 * registered, and most importantly, no beans will have been instantiated yet.
	 * 修改应用上下文的内部 bean 工厂（在其标准初始化以后）。初始定义的资源会被加载，但没有后置处理器
	 * 运行，也不会注册派生的 bean，最重要的是，还没有 bean 被实例化。
	 * <p>This template method allows for registering special BeanPostProcessors
	 * etc in certain AbstractApplicationContext subclasses.
	 * 这个模板方法允许在具体的 AbstractApplicationContext 子类中注册特殊的 BeanPostProcessor。
	 *
	 * @param beanFactory the bean factory used by the application context
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * 按照显式指定的顺序，初始化并调用所有已注册的 BeanFactoryPostProcessor bean。
	 * <p>Must be called before singleton instantiation.
	 * 在初始化单例bean之前必须要被调用。
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 最终调用 BeanFactoryPostProcessor.postProcessBeanFactory()
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// 检测 LoadTimeWeaver，如果找到的话，准备织入。
		// (for example, through an @Bean method registered by ConfigurationClassPostProcessor)
		// 如通过 ConfigurationClassPostProcessor 注册的 @Bean 方法
		if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null &&
				beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// 设置一个临时类加载器，用于类型匹配。
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * Instantiate and register all BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * 按照显式指定的顺序，初始化并注册所有的 BeanPostProcessor bean。
	 * <p>Must be called before any instantiation of application beans.
	 * 在任何 application bean 被初始化前必须被调用。
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 最终调用 ConfigurableBeanFactory.addBeanPostProcessor()
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the {@link MessageSource}.
	 * <p>Uses parent's {@code MessageSource} if none defined in this context.
	 * 初始化 MessageSource，如果在上下文中未定义，则使用父类的。
	 *
	 * @see #MESSAGE_SOURCE_BEAN_NAME
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// bean 名为"messageSource"
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource hms &&
					hms.getParentMessageSource() == null) {
				// Only set parent context as parent MessageSource if no parent MessageSource
				// registered already.
				// 为 messageSource 属性设置父 MessageSource
				hms.setParentMessageSource(getInternalParentMessageSource());
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		} else {
			// Use empty MessageSource to be able to accept getMessage calls.
			// 设置默认的 messageSource，使用空的 MessageSource 来接受对 getMessage() 的调用。
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			// 将该 messageSource 注册为一个单例
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the {@link ApplicationEventMulticaster}.
	 * <p>Uses {@link SimpleApplicationEventMulticaster} if none defined in the context.
	 * 初始化 ApplicationEventMulticaster，如果在上下文中未定义，则使用 SimpleApplicationEventMulticaster。
	 *
	 * @see #APPLICATION_EVENT_MULTICASTER_BEAN_NAME
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// bean 名为"applicationEventMulticaster"
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		} else {
			// 默认使用 SimpleApplicationEventMulticaster
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			// 注册为单例
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the {@link LifecycleProcessor}.
	 * <p>Uses {@link DefaultLifecycleProcessor} if none defined in the context.
	 * 初始化 LifecycleProcessor。如果在上下文中未定义，则使用 DefaultLifecycleProcessor。
	 *
	 * @see #LIFECYCLE_PROCESSOR_BEAN_NAME
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 * @since 3.0
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// bean 名为"lifecycleProcessor"
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor = beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		} else {
			// 默认使用 DefaultLifecycleProcessor
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			// 将其注册为单例
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 * 模板方法，默认实现为空，可被重写，以添加特殊上下文的刷新工作。在单例 bean 初始化前、初始化特殊 bean 时被调用。
	 *
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 * 向组播器中添加监听器，即实现了 ApplicationListener 接口的监听器 bean。不影响其他不是 bean 的监听器。
	 */
	protected void registerListeners() {
		// Register statically specified listeners first.
		// 注册静态指定的监听器列表
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		// 不要在此处初始化 FactoryBeans：我们需要保持所有常规 bean 未初始化，以便让后置处理器应用于它们！
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			// 将监听器添加到组播器中
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
		// 发布一下之前的早期应用事件，现在，我们终于有了组播器
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				// 组播早期事件
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 * 完成该上下文 bean 工厂的初始化，初始化所有剩余单例 bean。
	 * 1. 为该上下文初始化引导器 Executor；
	 * 2. 初始化此上下文的转换服务 ConversionService；
	 * 3. 注册一个默认的嵌入值解析器 StringValueResolver；
	 * 4. 初始化 BeanFactoryInitializer bean；
	 * 5. 初始化 LoadTimeWeaverAware bean；
	 * 6. 缓存（冻结）所有 bean 定义元数据；
	 * 7. 初始化所有剩余（非懒加载的）单例;
	 */
	@SuppressWarnings("unchecked")
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize bootstrap executor for this context.
		// 为该上下文初始化引导器，bean 名为"bootstrapExecutor"
		if (beanFactory.containsBean(BOOTSTRAP_EXECUTOR_BEAN_NAME) &&
				beanFactory.isTypeMatch(BOOTSTRAP_EXECUTOR_BEAN_NAME, Executor.class)) {
			beanFactory.setBootstrapExecutor(
					beanFactory.getBean(BOOTSTRAP_EXECUTOR_BEAN_NAME, Executor.class));
		}

		// Initialize conversion service for this context.
		// 初始化此上下文的转换服务，bean 名为"conversionService"
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no BeanFactoryPostProcessor
		// (such as a PropertySourcesPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		// 如果之前没有注册任何 BeanFactoryPostProcessor（例如 PropertySourcesPlaceholderConfigurer bean），
		// 则注册一个默认的嵌入值解析器：此时，主要用于注解属性值的解析。
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// Call BeanFactoryInitializer beans early to allow for initializing specific other beans early.
		// 尽早调用 BeanFactoryInitializer bean，以允许尽早初始化特定的其他 bean。
		String[] initializerNames = beanFactory.getBeanNamesForType(BeanFactoryInitializer.class, false, false);
		for (String initializerName : initializerNames) {
			beanFactory.getBean(initializerName, BeanFactoryInitializer.class).initialize(beanFactory);
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		// 尽早初始化 LoadTimeWeaverAware bean，以便尽早注册它们的转换器。
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			try {
				beanFactory.getBean(weaverAwareName, LoadTimeWeaverAware.class);
			} catch (BeanNotOfRequiredTypeException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to initialize LoadTimeWeaverAware bean '" + weaverAwareName +
							"' due to unexpected type mismatch: " + ex.getMessage());
				}
			}
		}

		// Stop using the temporary ClassLoader for type matching.
		// 停止使用临时 ClassLoader 进行类型匹配。
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		// 允许缓存（冻结）所有 bean 定义元数据，不会再作更改。
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		// 初始化所有剩余（非懒加载的）单例
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 * 结束该上下文的刷新，调用 LifecycleProcessor 的 onRefresh() 方法以及发布 ContextRefreshedEvent 事件。
	 * 1. 清空反射元数据缓存；
	 * 2. 清空上下文级别的资源缓存，如 ASM 元数据；
	 * 3. 为当前上下文初始化一个 LifecycleProcessor 处理器；
	 * 4. 向 lifecycle 处理器传播刷新事件，调用 lifecycle 处理器的 onRefresh() 方法；
	 * 5. 发布 ContextRefreshedEvent 事件；
	 */
	protected void finishRefresh() {
		// Reset common introspection caches in Spring's core infrastructure.
		// 重置 Spring 核心基础架构中的常用内省缓存，即清空反射元数据缓存
		resetCommonCaches();

		// Clear context-level resource caches (such as ASM metadata from scanning).
		// 清空上下文级别的资源缓存，如 ASM 元数据
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
		// 为当前上下文初始化一个 LifecycleProcessor 处理器
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		// 向 lifecycle 处理器传播刷新事件
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		// 发布最终事件 ContextRefreshedEvent
		publishEvent(new ContextRefreshedEvent(this));
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 * 在引发异常后取消上下文的刷新，重置 active 标志为 false。
	 *
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(Throwable ex) {
		this.active.set(false);

		// Reset common introspection caches in Spring's core infrastructure.
		// 重置 Spring 核心基础架构中的通用内省缓存。
		resetCommonCaches();
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * 重置 Spring 的通用反射元数据缓存，特别是 ReflectionUtils、AnnotationUtils、
	 * ResolvableType、CachedIntrospectionResults 的缓存。
	 *
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 * @since 4.2
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}

	@Override
	public void clearResourceCaches() {
		// 清空该资源加载器中的所有资源缓存。
		super.clearResourceCaches();
		if (this.resourcePatternResolver instanceof PathMatchingResourcePatternResolver pmrpr) {
			// Clear the local resource cache, removing all cached classpath/jar structures.
			// 清空本地资源缓存，删除所有缓存的 classpath/jar 结构。
			pmrpr.clearCache();
		}
	}


	/**
	 * Register a shutdown hook {@linkplain Thread#getName() named}
	 * {@code SpringContextShutdownHook} with the JVM runtime, closing this
	 * context on JVM shutdown unless it has already been closed at that time.
	 * 使用 JVM runtime 注册一个名为"SpringContextShutdownHook"的关闭钩子，在 JVM
	 * 关闭时，关闭当前上下文，除非此时上下文已被关闭。
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * 具体关闭逻辑代理到了 doClose() 方法。
	 *
	 * @see Runtime#addShutdownHook
	 * @see ConfigurableApplicationContext#SHUTDOWN_HOOK_THREAD_NAME
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					if (isStartupShutdownThreadStuck()) {
						active.set(false);
						return;
					}
					startupShutdownLock.lock();
					try {
						doClose();
					} finally {
						startupShutdownLock.unlock();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Determine whether an active startup/shutdown thread is currently stuck,
	 * for example, through a {@code System.exit} call in a user component.
	 * 判断一个活动的启动/关闭的线程当前是否卡住，如用户组件中的调用了 System.exit。
	 */
	private boolean isStartupShutdownThreadStuck() {
		// 当前调动或关闭线程
		Thread activeThread = this.startupShutdownThread;
		if (activeThread != null && activeThread.getState() == Thread.State.WAITING) {
			// Indefinitely waiting: might be Thread.join or the like, or System.exit
			// 无限期等待：可能是 Thread.join 或类似内容，或者是 System.exit
			activeThread.interrupt();
			try {
				// Leave just a little bit of time for the interruption to show effect
				// 留出一点时间让中断显示效果
				Thread.sleep(1);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			if (activeThread.getState() == Thread.State.WAITING) {
				// Interrupted but still waiting: very likely a System.exit call
				// 中断了，但仍然等待：很可能是是一个 System.exit 调用
				return true;
			}
		}
		return false;
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * 关闭应用上下文，销毁 bean 工厂中的所有 bean。实际执行逻辑代理到了 doClose() 方法。
	 * 此外，还会删除 JVM 关闭钩子（如果已注册），因为不再需要它。
	 *
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		if (isStartupShutdownThreadStuck()) {
			this.active.set(false);
			return;
		}

		// 加锁
		this.startupShutdownLock.lock();
		try {
			// 线程赋值，当前线程为关闭线程
			this.startupShutdownThread = Thread.currentThread();

			// 关闭处理
			doClose();

			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			// 如果注册了一个钩子，则不再需要了：已经显式关闭了上下文。
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				} catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		} finally {
			this.startupShutdownThread = null;
			this.startupShutdownLock.unlock();
		}
	}

	/**
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * 上下文关闭操作的实际执行方法：发布 ContextClosedEvent 事件，销毁 bean 工厂中的单例。
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 * 被 close() 调用，或 JVM 的关闭钩子（如果有的话）。
	 *
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		// Check whether an actual close attempt is necessary...
		// 检查下标记，确认是否需要执行关闭操作，closed 是在此处设置为 true 的（开始关闭时）
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			try {
				// Publish shutdown event.
				// 发布关闭事件。
				publishEvent(new ContextClosedEvent(this));
			} catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			// 停止所有 Lifecycle bean，避免在单个销毁期间出现延迟。
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				} catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory.
			// 销毁该上下文管理的所有 bean。
			destroyBeans();

			// Close the state of this context itself.
			// 关闭上下文本身的状态。
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			// 让子类做一些其希望的最终清理动作……模板方法，默认什么也不做，由子类重写
			onClose();

			// Reset common introspection caches to avoid class reference leaks.
			// 重置通用内省缓存，避免类引用泄露。
			resetCommonCaches();

			// Reset local application listeners to pre-refresh state.
			// 重置本地应用监听器为预刷新状态
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// Reset internal delegates.
			// 重置内部代理
			this.applicationEventMulticaster = null;
			this.messageSource = null;
			this.lifecycleProcessor = null;

			// Switch to inactive.
			// 设置 active 标记为 false
			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * 模板方法，用于销毁该上下文管理的所有 bean。默认实现为调用 DisposableBean.destroy() 或
	 * 指定的销毁方法，销毁上下文中所有缓存的单例。
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 * 可被重写，以在上下文的 BeanFactory 仍处于活动时，在标准单例被销毁前或后，添加上下文特殊的 bean 销毁步骤。
	 *
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * 可被重写的模板方法，以添加上下文特殊的关闭工作。
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 * 在 doClose() 关闭过程结束时被调用，在上下文的 BeanFactory 关闭之后。如果自定义的关闭逻辑
	 * 需要在 BeanFactory 还处于激活的时候调用，则重写 destroyBeans()。
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isClosed() {
		return this.closed.get();
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * 断言上下文的 BeanFactory 处于活动状态，如果不是，则抛出 IllegalStateException 异常。
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * 由所有依赖于活动上下文的 BeanFactory 委托方法调用，特别是所有 bean 访问器方法。
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 * 默认实现为检查上下文 active 标记的状态。可被改写以实现更具体的检查。或当 getBeanFactory()
	 * 本身会抛出一个异常时该方法不做操作。
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			} else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit);
	}

	@Override
	public <A extends Annotation> Set<A> findAllAnnotationsOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		// 返回 bean 上指定类型的注解
		return getBeanFactory().findAllAnnotationsOnBean(beanName, annotationType, allowFactoryBeanInit);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 * 如果当前上下文继承自 ConfigurableApplicationContext，则返回父上下文的内部 bean 工厂，
	 * 否则返回父上下文（AbstractApplicationContext 实现了 BeanFactory 接口）。
	 *
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext cac ?
				cac.getBeanFactory() : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * 返回当前上下文使用的内部 MessageSource，如果没有，则抛出异常。
	 *
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 * 如果父类也是一个 AbstractApplicationContext，则返回其内部 MessageSource，
	 * 否则，返回父类上下文本身（AbstractApplicationContext 实现了 MessageSource 接口）。
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext abstractApplicationContext ?
				abstractApplicationContext.messageSource : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// Abstract methods that must be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 * 子类必须实现该方法，以执行实际的配置加载。该方法在refresh()做其他初始化工作前被调用。子类要么创建一个
	 * 新的bean工厂并持有对它的引用，或者返回一个它持有的单个BeanFactory工厂实例。对于后一种情况，如果多次
	 * 刷新上下文时，通常会抛出一个IllegalStateException异常。
	 *
	 * @throws BeansException        if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 *                               attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 * 子类必须实现该方法，以释放其内部bean工厂。该方法在 close() 处理完其他关闭工作后调用。
	 */
	protected abstract void closeBeanFactory();

	/**
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 * 在这里子类必须要返回其内部bean工厂。子类应有效实现查找，以在重复调用时不会有性能损失。在返回内部工厂前，
	 * 子类应该检查上下文是否仍然处理活动状态。当上下文关闭后，内部工厂通常来说应该是不可再访问的。
	 *
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 *                               (usually if {@link #refresh()} has never been called) or if the context has been
	 *                               closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
