from phi import *
def f(n):
    n_0 = n;
    a_0=None;a_5=None;a_1=None;a_3=None;a_2=None;a_4=None;a_6=None;b_0=None;b_2=None;b_1=None;b_3=None;c_0=None;i_0=None;i_3=None;i_2=None;i_4=None;fib_0=None;fib_2=None;fib_1=None;fib_3=None;

    fib_0=0 
    i_0=0 
    a_0=0 
    b_0=1 
    c_0=2 
    phi0 = Phi()
    while phi0.phiLoopTest(i_0,i_2)<n_0:
        phi0.set()
        a_5 = phi0.phiEntry(a_0,a_4)
        b_2 = phi0.phiEntry(b_0,b_1)
        i_3 = phi0.phiEntry(i_0,i_2)
        fib_2 = phi0.phiEntry(fib_0,fib_1)

        fib_1=a_5+b_2 
        a_1=b_2 
        phi1 = Phi()
        for i_1 in range(3):
            phi1.set()
            a_3 = phi1.phiEntry(a_1,a_2)

            a_2=a_3+1 
        a_4 = phi1.phiExit(a_1,a_2)
        b_1=fib_1 
        i_2=i_1+1 
    a_6 = phi0.phiExit(a_0,a_4)
    b_3 = phi0.phiExit(b_0,b_1)
    i_4 = phi0.phiExit(i_0,i_2)
    fib_3 = phi0.phiExit(fib_0,fib_1)
    print(fib_3) 



#generate python causal map
causal_map = {'b_0':[],'a_1':['b_2'],'a_0':[],'b_2':['b_0','b_1','i_0','i_2','n_0'],'a_3':['a_1','a_2','i_1'],'c_0':[],'a_2':['a_3'],'b_1':['fib_1'],'a_5':['a_0','a_4','i_0','i_2','n_0'],'a_4':['a_1','a_2','i_1'],'b_3':['b_0','b_1','i_0','i_2','n_0'],'a_6':['a_0','a_4','i_0','i_2','n_0'],'i_0':[],'i_3':['i_0','i_2','i_0','i_2','n_0'],'fib_2':['fib_0','fib_1','i_0','i_2','n_0'],'i_2':['i_1'],'fib_3':['fib_0','fib_1','i_0','i_2','n_0'],'fib_0':[],'fib_1':['a_5','b_2'],'i_4':['i_0','i_2','i_0','i_2','n_0'],}

#added phi names
phi_names_set = {'a_5','b_2','i_3','fib_2','a_3','a_4','a_6','b_3','i_4','fib_3',}