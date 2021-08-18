/*******************************************************************************
 * Copyright (c) 2021 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.services.explorer;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.sirius.web.core.api.ErrorPayload;
import org.eclipse.sirius.web.core.api.IEditingContext;
import org.eclipse.sirius.web.core.api.IInput;
import org.eclipse.sirius.web.core.api.IPayload;
import org.eclipse.sirius.web.services.api.representations.IRepresentationService;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeDescription;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeKind;
import org.eclipse.sirius.web.spring.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.web.spring.collaborative.api.Monitoring;
import org.eclipse.sirius.web.spring.collaborative.messages.ICollaborativeMessageService;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemInput;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemSuccessPayload;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * Handles representation deletion triggered via a tree item from the explorer.
 *
 * @author pcdavid
 */
@Service
public class DeleteRepresentationTreeItemEventHandler implements IEditingContextEventHandler {

    private final IRepresentationService representationService;

    private final ICollaborativeMessageService messageService;

    private final Counter counter;

    public DeleteRepresentationTreeItemEventHandler(IRepresentationService representationService, ICollaborativeMessageService messageService, MeterRegistry meterRegistry) {
        this.representationService = Objects.requireNonNull(representationService);
        this.messageService = Objects.requireNonNull(messageService);

        // @formatter:off
        this.counter = Counter.builder(Monitoring.EVENT_HANDLER)
                .tag(Monitoring.NAME, this.getClass().getSimpleName())
                .register(meterRegistry);
        // @formatter:on
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        // @formatter:off
        return input instanceof DeleteTreeItemInput &&
               !(this.isDomainObjectKind(((DeleteTreeItemInput) input).getKind()) ||
                 DeleteDocumentTreeItemEventHandler.DOCUMENT_ITEM_KIND.equals(((DeleteTreeItemInput) input).getKind()));
        // @formatter:on
    }

    private boolean isDomainObjectKind(String kind) {
        // TODO There should really a better way to do this...
        return kind != null && kind.contains("::"); //$NON-NLS-1$
    }

    @Override
    public void handle(One<IPayload> payloadSink, Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        this.counter.increment();

        String message = this.messageService.invalidInput(input.getClass().getSimpleName(), DeleteTreeItemInput.class.getSimpleName());
        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, editingContext.getId(), input);
        IPayload payload = new ErrorPayload(input.getId(), message);
        if (input instanceof DeleteTreeItemInput) {
            DeleteTreeItemInput deleteRepresentationInput = (DeleteTreeItemInput) input;
            UUID representationId = deleteRepresentationInput.getTreeItemId();
            if (this.representationService.existsById(representationId)) {
                this.representationService.delete(representationId);
                payload = new DeleteTreeItemSuccessPayload(input.getId());
                changeDescription = new ChangeDescription(ChangeKind.REPRESENTATION_DELETION, editingContext.getId(), input);
            }
        }
        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }
}