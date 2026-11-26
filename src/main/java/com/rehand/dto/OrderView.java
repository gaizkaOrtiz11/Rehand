package com.rehand.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.rehand.domain.Order;
import com.rehand.domain.OrderStatus;
import com.rehand.domain.User;

/**
 * The order detail page: the order (listing, seller, buyer and photos loaded), who is looking at it
 * and which actions that person may take in the current state. The template only reads flags,
 * the service re-checks every action anyway.
 */
public class OrderView {

    /** One step of the status timeline. state is "done", "current", "upcoming" or "cancelled". */
    public static final class Step {
        private final String label;
        private final LocalDateTime at;
        private final String state;

        Step(String label, LocalDateTime at, String state) {
            this.label = label;
            this.at = at;
            this.state = state;
        }

        public String getLabel() {
            return label;
        }

        public LocalDateTime getAt() {
            return at;
        }

        public String getState() {
            return state;
        }

        public boolean isCurrent() {
            return "current".equals(state);
        }
    }

    private final Order order;
    private final boolean buyer;
    private final boolean seller;
    private final boolean admin;
    private final int autoCompleteHours;

    public OrderView(Order order, Long viewerId, boolean admin, int autoCompleteHours) {
        this.order = order;
        this.buyer = order.isBuyer(viewerId);
        this.seller = order.isSeller(viewerId);
        this.admin = admin;
        this.autoCompleteHours = autoCompleteHours;
    }

    public Order getOrder() {
        return order;
    }

    public boolean isBuyer() {
        return buyer;
    }

    public boolean isSeller() {
        return seller;
    }

    /** An admin who is not a party of this order (an admin who bought something sees the buyer view). */
    public boolean isAdminView() {
        return admin && !buyer && !seller;
    }

    /** Seller for the buyer, buyer for the seller; for an admin the buyer (the seller is shown as well). */
    public User getCounterpart() {
        return seller ? order.getBuyer() : order.getSeller();
    }

    public String getCounterpartRole() {
        return seller ? "Buyer" : "Seller";
    }

    public String getCounterpartInitials() {
        return Initials.of(getCounterpart().getUsername());
    }

    public boolean isCanShip() {
        return seller && order.getStatus() == OrderStatus.PAID;
    }

    public boolean isCanConfirmDelivery() {
        return buyer && order.getStatus() == OrderStatus.SHIPPED;
    }

    public boolean isCanComplete() {
        return buyer && order.getStatus() == OrderStatus.DELIVERED;
    }

    public boolean isCanCancel() {
        return (buyer || seller || admin) && order.getStatus() == OrderStatus.PAID;
    }

    public boolean isHasActions() {
        return isCanShip() || isCanConfirmDelivery() || isCanComplete() || isCanCancel();
    }

    public int getAutoCompleteHours() {
        return autoCompleteHours;
    }

    /** When the scheduler releases the payment of a DELIVERED order, or null. */
    public LocalDateTime getAutoCompleteAt() {
        return order.getStatus() == OrderStatus.DELIVERED && order.getDeliveredAt() != null
                ? order.getDeliveredAt().plusHours(autoCompleteHours) : null;
    }

    /** Paid, Shipped, Delivered, Completed; a cancelled order shows Paid and Cancelled. */
    public List<Step> getTimeline() {
        List<Step> steps = new ArrayList<>();
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED) {
            steps.add(new Step("Paid", order.getCreatedAt(), "done"));
            steps.add(new Step("Cancelled", order.getCancelledAt(), "cancelled"));
            return steps;
        }
        int reached = status.ordinal(); // PAID 0, SHIPPED 1, DELIVERED 2, COMPLETED 3
        String[] labels = {"Paid", "Shipped", "Delivered", "Completed"};
        LocalDateTime[] times = {order.getCreatedAt(), order.getShippedAt(), order.getDeliveredAt(), order.getCompletedAt()};
        for (int i = 0; i < labels.length; i++) {
            String state;
            if (i < reached || (i == reached && status == OrderStatus.COMPLETED)) {
                state = "done";
            } else if (i == reached) {
                state = "current";
            } else {
                state = "upcoming";
            }
            steps.add(new Step(labels[i], i <= reached ? times[i] : null, state));
        }
        return steps;
    }

    /** One sentence that tells the viewer what happens next. */
    public String getNextStep() {
        return switch (order.getStatus()) {
            case PAID -> seller
                    ? "The buyer has paid. Pack the item and add the tracking code once it is on its way."
                    : "Payment received. The seller has been asked to ship the item.";
            case SHIPPED -> buyer
                    ? "The parcel is on its way. Confirm the delivery once it arrives."
                    : "The parcel is on its way to the buyer.";
            case DELIVERED -> buyer
                    ? "Check the item. If everything is fine, release the payment to the seller."
                    : "The buyer has received the item. The payment is released after their check.";
            case COMPLETED -> seller
                    ? "The order is complete and the payment is in your wallet."
                    : "The order is complete. The seller has been paid.";
            case CANCELLED -> buyer
                    ? "The order was cancelled and the full price was refunded to your wallet."
                    : "The order was cancelled and the buyer was refunded.";
        };
    }
}
