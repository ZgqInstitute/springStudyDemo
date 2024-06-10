/*
 * Copyright 2002-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *  这是祝光泉的spring学习注释
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		//(祝光泉) 定义一个集合，用于存放已经执行过的BeanDefinitionRegistryPostProcessor
		Set<String> processedBeans = new HashSet<>();

		//(祝光泉)1.判断beanFactory是否为BeanDefinitionRegistry，beanFactory为DefaultListableBeanFactory,而DefaultListableBeanFactory实现了BeanDefinitionRegistry接口，因此这边为true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			//(祝光泉) 用于存放普通的BeanFactoryPostProcessor
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			//(祝光泉) 用于存放BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			//(祝光泉) 2.首先处理框架自带的beanFactoryPostProcessors，遍历所有的beanFactoryPostProcessors, 将BeanDefinitionRegistryPostProcessor和普通BeanFactoryPostProcessor区分开
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;
					//(祝光泉) 直接执行BeanDefinitionRegistryPostProcessor接口的postProcessBeanDefinitionRegistry方法？？？？？？？？？？？？？？？？？？？？？？？
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					//(祝光泉) 添加到registryProcessors集合中(用于最后执行postProcessBeanFactory方法)
					registryProcessors.add(registryProcessor);
				}
				else {
					//(祝光泉) 若没有实现BeanDefinitionRegistryPostProcessor接口，那么就是普通的BeanFactoryPostProcessor，则加入到regularPostProcessors集合中
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement PriorityOrdered, Ordered, and the rest.
			//(祝光泉) 定义一个集合，用于保存当前要执行的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			//==================================================祝光泉===========================================================================
			//================1-调用所有实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor实现类=======================================
			//===================================================================================================================================

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			/**---祝光泉---
			 * 祝光泉Q：为什么这一步得到的beanName是org.springframework.context.annotation.internalConfigurationAnnotationProcessor？
			 * 祝光泉A：因为在创建容器(new AnnotationConfigApplicationContext())时，就向容器中注册(调用registry.registerBeanDefinition()方法)
			 *         beanName = org.springframework.context.annotation.internalConfigurationAnnotationProcessor
			 *         BeanDefinition = ConfigurationClassPostProcessor
			 *         所以，org.springframework.context.annotation.internalConfigurationAnnotationProcessor这玩意只是ConfigurationClassPostProcessor在容器中的beanName
			 *
			 *
			 * 从容器中查出所有实现了BeanDefinitionRegistryPostProcessor接口的bean名称，这个getBeanNamesForType方法只会得到框架自带
			 * 的BeanDefinitionRegistryPostProcessor和加载配置类，由于自定义的BeanDefinitionRegistryPostProcessor在配置类中，所以
			 * 容器中bean的定义还没有自定义的BeanDefinitionRegistryPostProcessor。
			 * 虽然这一步的getBeanNamesForType没有得到自定义的BeanDefinitionRegistryPostProcessor，但是这一步会加载配置类(ConfigurationClassPostProcessor)，
			 * 所以经过这一步后，我们自定义的BeanDefinitionRegistryPostProcessor的bean定义消息就加入到容器中了，等下一步的getBeanNamesForType
			 * 方法执行，就会得到我们自定义的BeanDefinitionRegistryPostProcessor。
			 *
			 * 注：
			 *  （1）这里不是直接得到ConfigurationClassPostProcessor，而是得到internalConfigurationAnnotationProcessor，
			 *       而internalConfigurationAnnotationProcessor在AnnotationConfigUtils这个类中，在AnnotationConfigUtils类中添加ConfigurationClassPostProcessor
			 *       ConfigurationClassPostProcessor实现了BeanFactoryPostProcessor和PriorityOrdered接口
			 *  （2）这个First执行的是框架自带的BeanDefinitionRegistryPostProcessor且实现了PriorityOrdered接口的类，
			 *       我们自定义的BeanDefinitionRegistryPostProcessor且实现了PriorityOrdered接口的类不在这个First执行
			 */
			String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			for (String ppName : postProcessorNames) {
				//(祝光泉) 判断是否实现了PriorityOrdered接口的
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					/**---祝光泉---
					 * 祝光泉Q：上面getBeanNamesForType得到的beanName是org.springframework.context.annotation.internalConfigurationAnnotationProcessor
					 *         为什么这一步的getBean()创建的对象却是ConfigurationClassPostProcessor？
					 * 祝光泉A：因为ConfigurationClassPostProcessor在容器中的beanName就是等于org.springframework.context.annotation.internalConfigurationAnnotationProcessor
					 *
					 * 显示的调用容器的getBean()创建出该对象然后加入到currentRegistryProcessors集合中去
					 * 注：这里的getBean()方法会创建ppName对应的bean实例对象
					 */
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//(祝光泉) 将已经执行过的BeanDefinitionRegistryPostProcessor加入processedBeans集合，避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			//(祝光泉) 对currentRegistryProcessors集合中BeanDefinitionRegistryPostProcessor进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//(祝光泉) 加入到用于保存到registryProcessors中(用于最后执行postProcessBeanFactory方法)
			registryProcessors.addAll(currentRegistryProcessors);
			/**---祝光泉---
			 * 遍历currentRegistryProcessors集合, 执行postProcessBeanDefinitionRegistry()方法（只执行框架自带的BeanDefinitionRegistryPostProcessor的
			 * postProcessBeanDefinitionRegistry()方法，postProcessBeanFactory()方法不在这一步执行）
			 *     注：这里典型的BeanDefinitionRegistryPostProcessor就是ConfigurationClassPostProcessor，
			 *     作用是：进行bean定义的加载 比如：解析@Configuration、@Import、@ComponentScan、@Bean注解，将bean的定义消息放到容器中
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			//(祝光泉) 调用完之后，马上clear
			currentRegistryProcessors.clear();

			//==================================================祝光泉===========================================================================
			//================2-调用所有实现了Ordered接口的BeanDefinitionRegistryPostProcessor实现类===============================================
			//===================================================================================================================================

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			/**---祝光泉---
			 * 祝光泉Q：这里为什么又要执行一次getBeanNamesForType方法？
			 * 祝光泉A：重复执行是因为执行完上面的BeanDefinitionRegistryPostProcessor,可能会新增了其他的BeanDefinitionRegistryPostProcessor, 因此需要重新查找
			 *
			 * 我们自定义的BeanDefinitionRegistryPostProcessor且实现了PriorityOrdered或Ordered接口的类，在这个Next里执行
			 */
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				//(祝光泉) 校验是否实现了Ordered接口，并且还未执行过
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					//(祝光泉) 显示的调用getBean()的方式获取出该对象然后加入到currentRegistryProcessors集合中去
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//(祝光泉) 将已经执行过的BeanDefinitionRegistryPostProcessor加入processedBeans集合，避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			//(祝光泉) 对currentRegistryProcessors集合中BeanDefinitionRegistryPostProcessor进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			/**---祝光泉---
			 * 遍历currentRegistryProcessors, 执行postProcessBeanDefinitionRegistry方法
			 * 这里是执行我们自定义的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法，
			 * postProcessBeanFactory()方法不在这一步执行
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			currentRegistryProcessors.clear();

			//==================================================祝光泉==============================================================
			//================3-调用没有实现任何优先级接口的BeanDefinitionRegistryPostProcessor=======================================
			//======================================================================================================================

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			//(祝光泉) 定义一个重复处理的开关变量 默认值为true
			boolean reiterate = true;
			while (reiterate) {
				//(祝光泉) 进入循环马上把开关变量给改为false
				reiterate = false;
				//(祝光泉) 去容器中获取BeanDefinitionRegistryPostProcessor的bean的处理器名称
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					//(祝光泉) 排除已经执行过的BeanDefinitionRegistryPostProcessor
					if (!processedBeans.contains(ppName)) {
						//(祝光泉) 显示的调用容器的getBean()获取出该对象然后加入到currentRegistryProcessors集合中去
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						//(祝光泉) 将已经执行过的BeanDefinitionRegistryPostProcessor加入processedBeans集合，避免后续重复执行
						processedBeans.add(ppName);
						//(祝光泉) 如果有BeanDefinitionRegistryPostProcessor被执行, 则有可能会产生新的BeanDefinitionRegistryPostProcessor,因此这边将reiterate赋值为true, 代表需要再循环查找一次
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				//(祝光泉) 遍历currentRegistryProcessors, 执行postProcessBeanDefinitionRegistry方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			/**---祝光泉---
			 * 调用所有BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法(BeanDefinitionRegistryPostProcessor继承自BeanFactoryPostProcessor)
			 * >调用框架自带的BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法，如：ConfigurationClassPostProcessor的postProcessBeanFactory方法
			 * >调用自定义的BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			/**----祝光泉---
			 * 最后, 调用入参beanFactoryPostProcessors中的普通BeanFactoryPostProcessor的postProcessBeanFactory方法
			 * 这一步执行了什么？？？？？？？？？？？？？？？？？？？？？？？？
			 */
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		// (祝光泉)这个else什么情况执行？
		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		//=====================================================祝光泉================================================================================
		//到这里 , 入参beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理完毕。
		//下面开始处理容器中的所有普通的BeanFactoryPostProcessor
		//===========================================================================================================================================


		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		//(祝光泉) 获取实现了BeanFactoryPostProcessor接口的所有实现类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered、Ordered, and the rest.
		//(祝光泉) 将BeanFactoryPostProcessor处理器分成几种来执行 priorityOrdered、ordered、nonOrdered

		//(祝光泉) 定义一个集合，用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//(祝光泉) 定义一个集合，用于存放实现了Ordered接口的BeanFactoryPostProcessor的beanName
		List<String> orderedPostProcessorNames = new ArrayList<>();
        //(祝光泉) 定义一个集合，用于存放普通BeanFactoryPostProcessor的beanName
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		//(祝光泉) 遍历postProcessorNames, 将BeanFactoryPostProcessor按实现PriorityOrdered、实现Ordered接口、普通三种区分开
		for (String ppName : postProcessorNames) {
			//(祝光泉) 跳过已经执行过的
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			//(祝光泉) 如果是实现了PriorityOrdered接口的话，添加到priorityOrderedPostProcessors集合中
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			//(祝光泉) 如果是实现了Ordered接口的话，添加到orderedPostProcessors集合中
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			//(祝光泉) 剩下的就加到这个nonOrderedPostProcessorNames集合中
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		//================================================1-调用所有实现PriorityOrdered接口的BeanFactoryPostProcessor========================================================================
		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		//(祝光泉) 对priorityOrderedPostProcessors集合的BeanFactoryPostProcessor进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		//(祝光泉) 首先，执行priorityOrderedPostProcessors集合中BeanFactoryPostProcessor的postProcessBeanFactory()方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		//======================================================2-调用所有实现Ordered接口的BeanFactoryPostProcessor===========================================================================
		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.

		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		//(祝光泉) 遍历orderedPostProcessors集合, 执行postProcessBeanFactory()方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		//==============================================================3-调用所有剩下的BeanFactoryPostProcessor===============================================================================
		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//(祝光泉) 遍历nonOrderedPostProcessors集合, 执行postProcessBeanFactory()方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		//(祝光泉) 清除元数据缓存（mergedBeanDefinitions、allBeanNamesByType、singletonBeanNamesByType），因为后处理器可能已经修改了原始元数据，例如， 替换值中的占位符...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		/**祝光泉
		 * 执行这个getBeanNamesForType()方法会得到所有框架自带和自定义的BeanPostProcessor，
		 * 其中自动装配的BeanPostProcessor(AutowiredAnnotationBeanPostProcessor)也是这里得到
		 */
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 祝光泉 创建4个集合 分别存放不同的BeanPostProcessor
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();// 祝光泉 存放实现了PriorityOrdered接口的BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();// 祝光泉 存放实现了PriorityOrdered接口而且是MergedBeanDefinitionPostProcessor的BeanPostProcessor
		List<String> orderedPostProcessorNames = new ArrayList<>();// 祝光泉 存放实现了Ordered接口的BeanPostProcessor
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();// 祝光泉 存放没有实现了优先级接口的BeanPostProcessor

		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process").tag("postProcessor", postProcessor::toString);
			/**
			 * 调用BeanFactoryPostProcessor接口实现类的postProcessBeanFactory()方法
			 */
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
