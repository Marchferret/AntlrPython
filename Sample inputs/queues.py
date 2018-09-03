def _run_once(self):
    """Run one full iteration of the event loop.

    This calls all currently ready callbacks, polls for I/O,
    schedules the resulting callbacks, and finally schedules
    'call_later' callbacks.
    """

    sched_count = len(self._scheduled)
    if (sched_count > _MIN_SCHEDULED_TIMER_HANDLES and
                    self._timer_cancelled_count / sched_count >
                _MIN_CANCELLED_TIMER_HANDLES_FRACTION):
        # Remove delayed calls that were cancelled if their number
        # is too high
        new_scheduled = []
        for handle in self._scheduled:
            if handle._cancelled:
                handle._scheduled = False
            else:
                new_scheduled.append(handle)

        heapq.heapify(new_scheduled)
        self._scheduled = new_scheduled
        self._timer_cancelled_count = 0
    else:
        # Remove delayed calls that were cancelled from head of queue.
        while self._scheduled and self._scheduled[0]._cancelled:
            self._timer_cancelled_count -= 1
            handle = heapq.heappop(self._scheduled)
            handle._scheduled = False

    timeout = None
    if self._ready or self._stopping:
        timeout = 0
    elif self._scheduled:
        # Compute the desired timeout.
        when = self._scheduled[0]._when
        timeout = max(0, when - self.time())

    if self._debug and timeout != 0:
        t0 = self.time()
        event_list = self._selector.select(timeout)
        dt = self.time() - t0
        if dt >= 1.0:
            level = logging.INFO
        else:
            level = logging.DEBUG
        nevent = len(event_list)
        if timeout is None:
            logger.log(level, 'poll took %.3f ms: %s events',
                       dt * 1e3, nevent)
        elif nevent:
            logger.log(level,
                       'poll %.3f ms took %.3f ms: %s events',
                       timeout * 1e3, dt * 1e3, nevent)
        elif dt >= 1.0:
            logger.log(level,
                       'poll %.3f ms took %.3f ms: timeout',
                       timeout * 1e3, dt * 1e3)
    else:
        event_list = self._selector.select(timeout)
    self._process_events(event_list)

    # Handle 'later' callbacks that are ready.
    end_time = self.time() + self._clock_resolution
    while self._scheduled:
        handle = self._scheduled[0]
        if handle._when >= end_time:
            break
        handle = heapq.heappop(self._scheduled)
        handle._scheduled = False
        self._ready.append(handle)

    # This is the only place where callbacks are actually *called*.
    # All other places just add them to ready.
    # Note: We run all currently scheduled callbacks, but not any
    # callbacks scheduled by callbacks run this time around --
    # they will be run the next time (after another I/O poll).
    # Use an idiom that is thread-safe without using locks.
    ntodo = len(self._ready)
    for i in range(ntodo):
        handle = self._ready.popleft()
        if handle._cancelled:
            continue
        if self._debug:
            try:
                self._current_handle = handle
                t0 = self.time()
                handle._run()
                dt = self.time() - t0
                if dt >= self.slow_callback_duration:
                    logger.warning('Executing %s took %.3f seconds',
                                   _format_handle(handle), dt)
            finally:
                self._current_handle = None
        else:
            handle._run()
    handle = None  # Needed to break cycles when an exception occurs.