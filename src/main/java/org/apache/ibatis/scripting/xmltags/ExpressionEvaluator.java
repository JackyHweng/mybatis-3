/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BuilderException;

/**
 * @author Clinton Begin
 */
// OGNL 表达式计算器
public class ExpressionEvaluator {


  /**
   * 判断表达式对应的值
   * @param expression 表达式
   * @param parameterObject 参数对象
   * @return
   */
  public boolean evaluateBoolean(String expression, Object parameterObject) {
    // 从Ognl 缓存中 获取表达式的值
    Object value = OgnlCache.getValue(expression, parameterObject);
    // 如果是 Boolean 类型，直接返回
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    // 如果是数字类型 则按照 0 1 返回true false
    if (value instanceof Number) {
      return new BigDecimal(String.valueOf(value)).compareTo(BigDecimal.ZERO) != 0;
    }
    // 其他类型，判断是否为空
    return value != null;
  }

  // 获取表达式对应的集合
  public Iterable<?> evaluateIterable(String expression, Object parameterObject) {
    // 获取表达式的值
    Object value = OgnlCache.getValue(expression, parameterObject);
    if (value == null) {
      throw new BuilderException("The expression '" + expression + "' evaluated to a null value.");
    }
    // 如果对象是可以迭代的，返回迭代对象
    if (value instanceof Iterable) {
      return (Iterable<?>) value;
    }
    // 数组
    if (value.getClass().isArray()) {
      // the array may be primitive, so Arrays.asList() may throw
      // a ClassCastException (issue 209).  Do the work manually
      // Curse primitives! :) (JGB)
      int size = Array.getLength(value);
      List<Object> answer = new ArrayList<>();
      for (int i = 0; i < size; i++) {
        Object o = Array.get(value, i);
        answer.add(o);
      }
      return answer;
    }
    // p
    if (value instanceof Map) {
      return ((Map) value).entrySet();
    }
    // 其他类型不能被迭代
    throw new BuilderException("Error evaluating expression '" + expression + "'.  Return value (" + value + ") was not iterable.");
  }

}
