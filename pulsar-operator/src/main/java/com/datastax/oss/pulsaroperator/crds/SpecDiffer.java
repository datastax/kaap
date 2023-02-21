/*
 * Copyright DataStax, Inc.
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
package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import java.util.Arrays;

public class SpecDiffer {

    private SpecDiffer() {
    }

    public static boolean specsAreEquals(Object spec1, Object spec2) {
        if (spec1 == null && spec2 == null) {
            return true;
        }
        if (spec1 == null) {
            return false;
        }
        if (spec2 == null) {
            return false;
        }
        return Arrays.equals(SerializationUtil.writeAsJsonBytes(spec1), SerializationUtil.writeAsJsonBytes(spec2));
    }

    public static boolean specsAreEquals(Object spec1, byte[] spec2) {
        if (spec1 == null && spec2 == null) {
            return true;
        }
        if (spec1 == null) {
            return false;
        }
        if (spec2 == null) {
            return false;
        }
        return Arrays.equals(SerializationUtil.writeAsJsonBytes(spec1), spec2);
    }

}
