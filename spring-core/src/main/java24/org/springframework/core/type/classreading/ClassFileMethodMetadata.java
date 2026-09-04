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

import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

/**
 * {@link MethodMetadata} extracted from class bytecode using the
 * {@link java.lang.classfile.ClassFile} API.
 *
 * <p>Stores access flags as {@code int} and the method descriptor as a
 * {@link String} so retained metadata does not hold {@code AccessFlags} or
 * {@code MethodTypeDesc} objects from the parsed class file.
 *
 * @author Brian Clozel
 * @author Lordwill Kandiro
 * @since 7.0
 */
final class ClassFileMethodMetadata implements MethodMetadata {

	private final String methodName;

	private final int access;

	private final @Nullable String declaringClassName;

	private final String returnTypeName;

	// The source implements equals(), hashCode(), and toString() for the underlying method.
	private final Object source;

	private final MergedAnnotations mergedAnnotations;


	ClassFileMethodMetadata(String methodName, int access, @Nullable String declaringClassName,
			String returnTypeName, Object source, MergedAnnotations mergedAnnotations) {

		this.methodName = methodName;
		this.access = access;
		this.declaringClassName = declaringClassName;
		this.returnTypeName = returnTypeName;
		this.source = source;
		this.mergedAnnotations = mergedAnnotations;
	}


	@Override
	public String getMethodName() {
		return this.methodName;
	}

	@Override
	public @Nullable String getDeclaringClassName() {
		return this.declaringClassName;
	}

	@Override
	public String getReturnTypeName() {
		return this.returnTypeName;
	}

	@Override
	public boolean isAbstract() {
		return hasAccessFlag(AccessFlag.ABSTRACT);
	}

	@Override
	public boolean isStatic() {
		return hasAccessFlag(AccessFlag.STATIC);
	}

	@Override
	public boolean isFinal() {
		return hasAccessFlag(AccessFlag.FINAL);
	}

	@Override
	public boolean isOverridable() {
		return !isStatic() && !isFinal() && !isPrivate();
	}

	private boolean isPrivate() {
		return hasAccessFlag(AccessFlag.PRIVATE);
	}

	public boolean isSynthetic() {
		return hasAccessFlag(AccessFlag.SYNTHETIC);
	}

	public boolean isDefaultConstructor() {
		return this.methodName.equals("<init>");
	}

	private boolean hasAccessFlag(AccessFlag flag) {
		return (this.access & flag.mask()) != 0;
	}

	@Override
	public MergedAnnotations getAnnotations() {
		return this.mergedAnnotations;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ClassFileMethodMetadata that && this.source.equals(that.source)));
	}

	@Override
	public int hashCode() {
		return this.source.hashCode();
	}

	@Override
	public String toString() {
		return this.source.toString();
	}


	static ClassFileMethodMetadata of(MethodModel methodModel, ClassLoader classLoader) {
		String methodName = methodModel.methodName().stringValue();
		int access = methodModel.flags().flagsMask();
		String declaringClassName = methodModel.parent()
				.map(parent -> ClassUtils.convertResourcePathToClassName(parent.thisClass().name().stringValue()))
				.orElse(null);
		MethodTypeDesc methodType = methodModel.methodTypeSymbol();
		String descriptor = methodType.descriptorString();
		String returnTypeName = ClassFileAnnotationMetadata.resolveTypeName(methodType.returnType());
		Source source = new Source(declaringClassName, access, methodName, descriptor);
		MergedAnnotations mergedAnnotations = methodModel.elementStream()
				.filter(RuntimeVisibleAnnotationsAttribute.class::isInstance)
				.map(RuntimeVisibleAnnotationsAttribute.class::cast)
				.findFirst()
				.map(annotations -> ClassFileAnnotationDelegate.createMergedAnnotations(methodName, annotations, classLoader))
				.orElseGet(() -> MergedAnnotations.of(Collections.emptyList()));
		return new ClassFileMethodMetadata(methodName, access, declaringClassName, returnTypeName, source, mergedAnnotations);
	}


	/**
	 * {@link MergedAnnotation} source.
	 */
	static final class Source {

		private final @Nullable String declaringClassName;

		private final int access;

		private final String methodName;

		private final String descriptor;

		Source(@Nullable String declaringClassName, int access, String methodName, String descriptor) {
			this.declaringClassName = declaringClassName;
			this.access = access;
			this.methodName = methodName;
			this.descriptor = descriptor;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof Source that &&
					Objects.equals(this.declaringClassName, that.declaringClassName) &&
					this.access == that.access &&
					Objects.equals(this.methodName, that.methodName) &&
					Objects.equals(this.descriptor, that.descriptor)));
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.declaringClassName, this.access, this.methodName, this.descriptor);
		}

		@Override
		public String toString() {
			MethodTypeDesc type = MethodTypeDesc.ofDescriptor(this.descriptor);
			StringBuilder builder = new StringBuilder();
			AccessFlag.maskToAccessFlags(this.access, AccessFlag.Location.METHOD).forEach(flag -> {
				builder.append(flag.name().toLowerCase(Locale.ROOT));
				builder.append(' ');
			});
			builder.append(ClassFileAnnotationMetadata.resolveTypeName(type.returnType()));
			builder.append(' ');
			builder.append(this.declaringClassName);
			builder.append('.');
			builder.append(this.methodName);
			builder.append('(');
			builder.append(Stream.of(type.parameterArray())
					.map(ClassFileAnnotationMetadata::resolveTypeName)
					.collect(Collectors.joining(",")));
			builder.append(')');
			return builder.toString();
		}
	}

}
