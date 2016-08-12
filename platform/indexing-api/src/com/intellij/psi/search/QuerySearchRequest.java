/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiReference;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import consulo.annotations.RequiredReadAction;

/**
 * @author peter
 */
public class QuerySearchRequest {
  public final Query<PsiReference> query;
  public final SearchRequestCollector collector;
  public final Processor<PsiReference> processor;

  public QuerySearchRequest(Query<PsiReference> query,
                            final SearchRequestCollector collector,
                            boolean inReadAction, final PairProcessor<PsiReference, SearchRequestCollector> processor) {
    this.query = query;
    this.collector = collector;
    if (inReadAction) {
      this.processor = new ReadActionProcessor<PsiReference>() {
        @RequiredReadAction
        @Override
        public boolean processInReadAction(PsiReference psiReference) {
              return processor.process(psiReference, collector);
        }
      };
    } else {
      this.processor = new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          return processor.process(psiReference, collector);
        }
      };
    }

  }

  public void runQuery() {
    query.forEach(processor);
  }
}
