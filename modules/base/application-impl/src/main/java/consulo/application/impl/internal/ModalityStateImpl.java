/*
 * Copyright 2013-2023 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.application.impl.internal;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/09/2023
 */
public class ModalityStateImpl {
  public static final IdeaModalityState ANY = new IdeaModalityState() {
    @Override
    public boolean dominates(@Nonnull IdeaModalityState anotherState) {
      return false;
    }

    @Override
    public String toString() {
      return "ANY";
    }
  };

  public static final IdeaModalityState NON_MODAL = new IdeaModalityStateEx() {
    @Override
    public String toString() {
      return "NON_MODAL";
    }
  };
}
