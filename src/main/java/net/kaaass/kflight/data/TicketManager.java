package net.kaaass.kflight.data;

import net.kaaass.kflight.data.entry.EntryFlight;
import net.kaaass.kflight.data.entry.EntryTicketOrder;
import net.kaaass.kflight.data.structure.LinkedQueue;
import net.kaaass.kflight.exception.BadRequestException;
import net.kaaass.kflight.exception.NotFoundException;

import java.util.stream.Collectors;

/**
 * 票务管理
 */
public class TicketManager {

    static final LinkedQueue<EntryTicketOrder> QUEUE = new LinkedQueue<>();

    public static boolean isFlightBooking(EntryFlight flight) {
        return flight.getState() == EntryFlight.State.BOOKING;
    }

    /**
     * 购票
     */
    public static synchronized EntryTicketOrder orderTicket(EntryTicketOrder order) throws BadRequestException {
        var flight = order.getFlight();
        if (!isFlightBooking(flight))
            throw new BadRequestException("本航班不接受购票！");
        if (flight.getRestCabin() > 0) {
            // 有余票，直接购票
            flight.setRestCabin(flight.getRestCabin() - 1);
            var tickets = flight.getTickets();
            order.setID(tickets.size());
            order.setState(EntryTicketOrder.State.DONE);
            tickets.add(order);
        } else {
            // 没有票，存入队列
            order.setState(EntryTicketOrder.State.QUEUED);
            QUEUE.push(order);
        }
        return order;
    }

    /**
     * 通过航班与客户手机号退票
     */
    public static synchronized void withdrawTicket(EntryFlight flight, String phone) throws NotFoundException, BadRequestException {
        if (!isFlightBooking(flight))
            throw new BadRequestException("航班已经不可退票！");
        var tickets = flight.getTickets();
        var found = tickets.parallelStream()
                .filter(order -> order.getPhone().equals(phone))
                .collect(Collectors.toList());
        if (found.size() <= 0)
            throw new NotFoundException("未找到此机票！");
        found.forEach(tickets::remove);
        flight.setRestCabin(flight.getRestCabin() + found.size());
        // TODO: 触发退票事件
    }

    /**
     * 检查队列中的票是否可售
     */
    public static synchronized void checkQueue() {
        var rest = new LinkedQueue<EntryTicketOrder>();
        while (!QUEUE.isEmpty()) {
            var order = QUEUE.popFront();
            var flight = order.getFlight();
            if (flight.getRestCabin() > 0) {
                // 有余票，直接购票
                flight.setRestCabin(flight.getRestCabin() - 1);
                var tickets = flight.getTickets();
                order.setID(tickets.size());
                order.setState(EntryTicketOrder.State.DONE);
                tickets.add(order);
            } else {
                // 没有票，存入队列
                order.setState(EntryTicketOrder.State.QUEUED);
                rest.push(order);
            }
        }
        // 未购票的继续排队
        while (!rest.isEmpty()) {
            var cur = rest.popFront();
            if (isFlightBooking(cur.getFlight()))
                QUEUE.push(cur);
        }
    }
}