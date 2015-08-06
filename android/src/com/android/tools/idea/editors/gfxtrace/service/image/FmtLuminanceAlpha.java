/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service.image;

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final public class FmtLuminanceAlpha extends Format {
  //<<<Start:Java.ClassBody:1>>>

  // Constructs a default-initialized {@link FmtLuminanceAlpha}.
  public FmtLuminanceAlpha() {}


  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-41, 60, -3, -8, -122, 43, 107, -76, -62, 125, -87, -75, -67, -54, 98, -41, -77, -76, -69, -13, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new FmtLuminanceAlpha(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      FmtLuminanceAlpha o = (FmtLuminanceAlpha)obj;
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      FmtLuminanceAlpha o = (FmtLuminanceAlpha)obj;
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
