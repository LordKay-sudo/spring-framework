/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.core.type.classreading;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the {@link java.lang.classfile.ClassFile}-based metadata used on JDK 24+
 * stores access flags and the method descriptor as primitive values, so retained
 * metadata does not hold {@code AccessFlags} or {@code MethodTypeDesc} objects from the
 * parsed class file.
 *
 * <p>This runs under the {@code java24Test} task, which places the multi-release JAR on
 * the classpath so that {@link MetadataReaderFactory#create(ClassLoader)} selects the
 * {@code ClassFileMetadataReaderFactory}.
 *
 * @author Lordwill Kandiro
 */
class ClassFileMethodMetadataTests {

	@Test
	void storesAccessFlagsAndDescriptorAsPrimitives() throws Exception {
		AnnotationMetadata annotationMetadata = MetadataReaderFactory.create(getClass().getClassLoader())
				.getMetadataReader(WithMethod.class.getName()).getAnnotationMetadata();
		assertThat(annotationMetadata.getClass().getSimpleName()).isEqualTo("ClassFileAnnotationMetadata");
		assertThat(annotationMetadata.getClass().getDeclaredField("access").getType()).isEqualTo(int.class);

		MethodMetadata method = annotationMetadata.getAnnotatedMethods(Tag.class.getName()).iterator().next();
		assertThat(method.getClass().getSimpleName()).isEqualTo("ClassFileMethodMetadata");
		assertThat(method.getClass().getDeclaredField("access").getType()).isEqualTo(int.class);

		Field sourceField = method.getClass().getDeclaredField("source");
		sourceField.setAccessible(true);
		Object source = sourceField.get(method);
		assertThat(source.getClass().getDeclaredField("access").getType()).isEqualTo(int.class);
		assertThat(source.getClass().getDeclaredField("descriptor").getType()).isEqualTo(String.class);
	}


	@Retention(RetentionPolicy.RUNTIME)
	@interface Tag {
	}

	static class WithMethod {

		@Tag
		public String test() {
			return "";
		}
	}

}
