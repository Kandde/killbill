/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.payment.core.sm;

import javax.annotation.Nullable;

import org.killbill.automaton.Operation;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PaymentEnteringStateCallback implements EnteringStateCallback {

    private final Logger logger = LoggerFactory.getLogger(PaymentEnteringStateCallback.class);

    protected final PaymentAutomatonDAOHelper daoHelper;
    protected final PaymentStateContext paymentStateContext;

    protected PaymentEnteringStateCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.paymentStateContext = paymentStateContext;
    }

    @Override
    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        logger.debug("Entering state {} with result {}", newState.getName(), operationResult);

        // If the transaction was not created -- for instance we had an exception in leavingState callback then we bail; if not, then update state:
        if (paymentStateContext.getPaymentTransactionModelDao() != null && paymentStateContext.getPaymentTransactionModelDao().getId() != null) {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = paymentStateContext.getPaymentInfoPlugin();
            final TransactionStatus paymentStatus = paymentPluginStatusToPaymentStatus(paymentInfoPlugin, operationResult);
            daoHelper.processPaymentInfoPlugin(paymentStatus, paymentInfoPlugin, newState.getName());
        }
    }

    private TransactionStatus paymentPluginStatusToPaymentStatus(@Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin, final OperationResult operationResult) {
        if (paymentInfoPlugin == null) {
            if (OperationResult.EXCEPTION.equals(operationResult)) {
                // We got an exception during the plugin call
                return TransactionStatus.PLUGIN_FAILURE;
            } else {
                // The plugin completed the call but returned null?! Bad plugin...
                return TransactionStatus.UNKNOWN;
            }
        }

        if (paymentInfoPlugin.getStatus() == null) {
            // The plugin completed the call but returned an incomplete PaymentInfoPlugin?! Bad plugin...
            return TransactionStatus.UNKNOWN;
        }

        switch (paymentInfoPlugin.getStatus()) {
            case UNDEFINED:
                return TransactionStatus.UNKNOWN;
            case PROCESSED:
                return TransactionStatus.SUCCESS;
            case PENDING:
                return TransactionStatus.PENDING;
            case ERROR:
                return TransactionStatus.PAYMENT_FAILURE;
            default:
                return TransactionStatus.UNKNOWN;
        }
    }
}