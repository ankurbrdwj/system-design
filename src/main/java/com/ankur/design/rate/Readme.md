2. Implement `RateServiceImpl::getRate` taking into the consideration that:
    1. exchange rates change once a minute
    2. the method will be called frequently - multiple times a second
    3. the method will be called by multiple threads