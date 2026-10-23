package com.lms.commerce.service;

import com.lms.commerce.domain.Invoice;
import com.lms.commerce.domain.Order;
import com.lms.commerce.repo.InvoiceRepository;
import com.lms.commerce.repo.OrderRepository;
import com.lms.commerce.web.CommerceDtos.InvoiceView;
import com.lms.identity.domain.User;
import com.lms.identity.web.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final OrderRepository orders;

    public InvoiceService(InvoiceRepository invoices, OrderRepository orders) {
        this.invoices = invoices;
        this.orders = orders;
    }

    /** One invoice per paid order; calling it again returns the existing one. */
    @Transactional
    public Invoice issue(Order order, User user) {
        return invoices.findByOrderId(order.getId()).orElseGet(() -> {
            Invoice i = new Invoice();
            i.setInvoiceNo("INV-" + order.getOrderNo().substring(4));
            i.setOrderId(order.getId());
            i.setUserId(order.getUserId());
            i.setAmountPaise(order.getTotalPaise());
            i.setCurrency(order.getCurrency());
            i.setBillingName(user.getFirstName() + " " + user.getLastName());
            i.setBillingEmail(user.getEmail());
            i.setDescription(order.getTitle());
            i.setIssuedAt(Instant.now());
            return invoices.save(i);
        });
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> mine(Long userId) {
        return invoices.findByUserIdOrderByIdDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceView get(Long userId, Long id) {
        return view(invoices.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Invoice not found")));
    }

    private InvoiceView view(Invoice i) {
        String orderNo = orders.findById(i.getOrderId()).map(Order::getOrderNo).orElse("");
        return new InvoiceView(i.getId(), i.getInvoiceNo(), i.getOrderId(), orderNo, i.getAmountPaise(), i.getCurrency(),
                i.getBillingName(), i.getBillingEmail(), i.getDescription(), i.getIssuedAt());
    }
}
