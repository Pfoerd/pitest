/*
 * Copyright 2014 Artem Khvastunov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.build.intercept.javafeatures;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pitest.bytecode.ASMVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * Example of code that was generated by 1.7 compiler for try-with-resources
 * block:
 *
 * <pre>
 * <code>
 * } finally {
 *   if (closeable != null) { // IFNULL
 *     if (localThrowable2 != null) { // IFNULL
 *       try {
 *         closeable.close(); // INVOKEVIRTUAL or INVOKEINTERFACE
 *       } catch (Throwable x2) {
 *         localThrowable2.addSuppressed(x2); // INVOKEVIRTUAL
 *       }
 *     } else {
 *       closeable.close(); // INVOKEVIRTUAL or INVOKEINTERFACE
 *     }
 *   }
 * } // ATHROW
 * </code>
 * </pre>
 *
 * This class considers that only auto generated code may have such sequence
 * without any line change. Such an approach make sense only for <strong>javac
 * compiler</strong>.
 * <p>
 * <strong>Eclipse Java Compiler</strong> as well as <strong>aspectj</strong>
 * have its own opinion how to compile try-with-resources block:
 *
 * <pre>
 * <code>
 * } finally {
 *   if (throwable1 == null) { // IFNONNULL
 *     throwable1 = throwable2;
 *   } else {
 *     if (throwable1 != throwable2) { // IF_ACMPEQ
 *       throwable1.addSuppressed(throwable2); // INVOKEVIRTUAL
 *     }
 *   }
 * } // ATHROW
 * </code>
 * </pre>
 *
 * @author Artem Khvastunov &lt;contact@artspb.me&gt;
 */
class TryWithResourcesMethodVisitor extends MethodVisitor {

  private static final List<Integer> JAVAC_CLASS_INS_SEQUENCE     = Arrays
                                                                      .asList(
                                                                          ASTORE, // store
                                                                                  // throwable
                                                                          ALOAD,
                                                                          IFNULL, // closeable
                                                                                  // !=
                                                                                  // null
                                                                          ALOAD,
                                                                          IFNULL, // localThrowable2
                                                                                  // !=
                                                                                  // null
                                                                          ALOAD,
                                                                          INVOKEVIRTUAL,
                                                                          GOTO, // closeable.close()
                                                                          ASTORE, // Throwable
                                                                                  // x2
                                                                          ALOAD,
                                                                          ALOAD,
                                                                          INVOKEVIRTUAL,
                                                                          GOTO, // localThrowable2.addSuppressed(x2)
                                                                          ALOAD,
                                                                          INVOKEVIRTUAL, // closeable.close()
                                                                          ALOAD,
                                                                          ATHROW);           // throw
                                                                                              // throwable

  private static final List<Integer> JAVAC_INTERFACE_INS_SEQUENCE = Arrays
                                                                      .asList(
                                                                          ASTORE, // store
                                                                                  // throwable
                                                                          ALOAD,
                                                                          IFNULL, // closeable
                                                                                  // !=
                                                                                  // null
                                                                          ALOAD,
                                                                          IFNULL, // localThrowable2
                                                                                  // !=
                                                                                  // null
                                                                          ALOAD,
                                                                          INVOKEINTERFACE,
                                                                          GOTO, // closeable.close()
                                                                          ASTORE, // Throwable
                                                                                  // x2
                                                                          ALOAD,
                                                                          ALOAD,
                                                                          INVOKEVIRTUAL,
                                                                          GOTO, // localThrowable2.addSuppressed(x2)
                                                                          ALOAD,
                                                                          INVOKEINTERFACE, // closeable.close()
                                                                          ALOAD,
                                                                          ATHROW);           // throw
                                                                                              // throwable

  private static final List<Integer> ECJ_INS_SEQUENCE             = Arrays
                                                                      .asList(
                                                                          ASTORE, // store
                                                                                  // throwable2
                                                                          ALOAD,
                                                                          IFNONNULL, // if
                                                                                     // (throwable1
                                                                                     // ==
                                                                                     // null)
                                                                          ALOAD,
                                                                          ASTORE,
                                                                          GOTO, // throwable1
                                                                                // =
                                                                                // throwable2;
                                                                          ALOAD,
                                                                          ALOAD,
                                                                          IF_ACMPEQ, // if
                                                                                     // (throwable1
                                                                                     // !=
                                                                                     // throwable2)
                                                                                     // {
                                                                          ALOAD,
                                                                          ALOAD,
                                                                          INVOKEVIRTUAL, // throwable1.addSuppressed(throwable2)
                                                                          ALOAD,
                                                                          ATHROW);           // throw
                                                                                              // throwable1

  private final Set<Integer> lines;

  private final List<Integer>        opcodesStack                 = new ArrayList<>();
  private int                        currentLineNumber;

  /**
   * @param lines
   *          to store detected line numbers
   */
  TryWithResourcesMethodVisitor(final Set<Integer> lines) {
    super(ASMVersion.ASM_VERSION);
    this.lines = lines;
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    prepareToStartTracking();
    this.currentLineNumber = line;
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    this.opcodesStack.add(opcode);
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    this.opcodesStack.add(opcode);
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name,
      String desc, boolean itf) {
    this.opcodesStack.add(opcode);
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void visitInsn(int opcode) {
    if (opcode == Opcodes.ATHROW) {
      this.opcodesStack.add(opcode);
      finishTracking();
    }
    super.visitInsn(opcode);
  }

  private void finishTracking() {
    if (JAVAC_CLASS_INS_SEQUENCE.equals(this.opcodesStack)
        || JAVAC_INTERFACE_INS_SEQUENCE.equals(this.opcodesStack)
        || ECJ_INS_SEQUENCE.equals(this.opcodesStack)) {
      this.lines.add(this.currentLineNumber);
    }
    prepareToStartTracking();
  }

  private void prepareToStartTracking() {
    if (!this.opcodesStack.isEmpty()) {
      this.opcodesStack.clear();
    }
  }
}
