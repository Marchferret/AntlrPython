from phi import *
def test(a,b,c,key):
    a_0 = a;b_0 = b;c_0 = c;key_0 = key;
    i_0=None;i_1=None;sum_0=None;sum_1=None;sum_3=None;sum_2=None;sum_4=None;sum_5=None;

    sum_0=0 
    if range(c=c_0):
        sum_1=a_0+b_0+c_0 
    else:
        i_0=0 
        phi0 = Phi()
        while i_0<a_0+b_0+c_0:
            phi0.set()
            sum_3 = phi0.phiEntry(sum_0,sum_2)

            sum_2 = sum_3+1
        sum_4 = phi0.phiExit(sum_0,sum_2)
    phiPreds = [range(c=c_0)]
    phiNames = [None,i_0]
    i_1= phiIf(phiPreds, phiNames)
    phiPreds = [range(c=c_0)]
    phiNames = [sum_1,sum_4]
    sum_5= phiIf(phiPreds, phiNames)
    return sum_5

print(test(1,3,10,1)) 


#generate python causal map
causal_map = {'sum_3':['sum_0','sum_2','b_0','a_0','c_0','i_0'],'sum_2':['sum_3'],'sum_1':['a_0','b_0','c_0'],'sum_0':[],'i_1':['i_0','c','c_0'],'sum_5':['sum_1','sum_4','c','c_0'],'i_0':[],'sum_4':['sum_0','sum_2','b_0','a_0','c_0','i_0'],}

#added phi names
phi_names_set = {'sum_3','sum_4','i_1','sum_5',}