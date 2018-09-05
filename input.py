def f(n):
    fib  = 0
    i = 0
    a = 0
    b = 1
    c = 2
    while i < n:
        fib = a + b
        a = b
        for i in range(3):
            a = a + 1
        b = fib
        i = i + 1

    print(fib)
