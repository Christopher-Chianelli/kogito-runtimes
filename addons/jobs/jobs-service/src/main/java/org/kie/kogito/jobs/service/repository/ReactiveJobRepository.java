/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.jobs.service.repository;

import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.ScheduledJob;

public interface ReactiveJobRepository {

    CompletionStage<ScheduledJob> save(ScheduledJob job);

    CompletionStage<ScheduledJob> get(String id);

    CompletionStage<Boolean> exists(String id);

    CompletionStage<ScheduledJob> delete(String id);

    PublisherBuilder<ScheduledJob> findByStatus(JobStatus... status);

    PublisherBuilder<ScheduledJob> findAll();

}
