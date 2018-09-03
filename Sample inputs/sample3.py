def test(a, b, c, key):
    sum = 0
    if key == 1:
        sum =  a + b + c
    elif key == 2:
        for i in range(0, a):
            for j in range(0, b):
                for k in range(0, c):
                    sum += 1
    else:
        i = 0
        while i < a + b + c:
            sum += 1
    return  sum
print(test(1 ,3 ,10, 1))
