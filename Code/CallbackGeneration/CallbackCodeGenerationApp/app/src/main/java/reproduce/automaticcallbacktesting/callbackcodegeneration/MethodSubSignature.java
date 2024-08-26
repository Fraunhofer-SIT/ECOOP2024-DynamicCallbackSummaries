package reproduce.automaticcallbacktesting.callbackcodegeneration;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2003 Ondrej Lhotak
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows one-time parsing of method subsignatures. Note that these method sub signatures are resolved, i.e. we resolve the
 * complete types upon construction.
 * 
 */
public class MethodSubSignature {
  public final String methodName;
  public final String returnType;
  public final List<String> parameterTypes;
  public final String numberedSubSig;
  private static final Pattern PATTERN_METHOD_SUBSIG
      = Pattern.compile("(?<returnType>.*?) (?<methodName>.*?)\\((?<parameters>.*?)\\)");


  public MethodSubSignature(String subsig) {
    this.numberedSubSig = subsig;
    Matcher m = PATTERN_METHOD_SUBSIG.matcher(subsig.toString());
    if (!m.matches()) {
      throw new IllegalArgumentException("Not a valid subsignature: " + subsig);
    }

    methodName = m.group(2);
    returnType = (m.group(1));
    String parameters = m.group(3);
    String[] spl = parameters.split(",");
    parameterTypes = new ArrayList<>(spl.length);

    if (parameters != null && !parameters.isEmpty()) {
      for (String p : spl) {
        parameterTypes.add((p.trim()));
      }
    }
  }




  public String getMethodName() {
    return methodName;
  }

  public String getReturnType() {
    return returnType;
  }

  public List<String> getParameterTypes() {
    return parameterTypes;
  }

  public String getNumberedSubSig() {
    return numberedSubSig;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
    result = prime * result + ((parameterTypes == null) ? 0 : parameterTypes.hashCode());
    result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MethodSubSignature other = (MethodSubSignature) obj;
    if (methodName == null) {
      if (other.methodName != null) {
        return false;
      }
    } else if (!methodName.equals(other.methodName)) {
      return false;
    }
    if (!parameterTypes.equals(other.parameterTypes)) {
      return false;
    }
    if (returnType == null) {
      if (other.returnType != null) {
        return false;
      }
    } else if (!returnType.equals(other.returnType)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return numberedSubSig.toString();
  }
}
