/** This is intended as the class defining the distance between two orthant extension spaces. It will include methods to compute this distance, as well was the pair of trees that obtain this smaller distance

Part of the package BHVExtMinDistance and it is constructed using tools from the packages: 
 * distanceAlg1; PolyAlg; constructed by Megan Owen

Part of the package that computes distances between Extension Spaces.
*/


package BHVExtMinDistance;

import java.util.*;
import distanceAlg1.*;
import polyAlg.*;
import static polyAlg.PolyMain.getGeodesic;

public class OrthExtDistance{
    //Trees in each Orthant extension space from which the shorter geodesic is obtained
    private PhyloTree Tree1;
    private PhyloTree Tree2;
    
    //Shorter distance in between the orthant extension space.
    private double Distance;
    private Geodesic FinalGeode;//Shorter geodesic
    
    //Quick function to determine if two BitSets represent the same bipartition.
    private boolean equivalentBip(BitSet Bit1, BitSet Bit2, int numberLeaves){
        return (Bit2.equals(Bit1) || ((!Bit2.intersects(Bit1)) && (Bit1.cardinality() + Bit2.cardinality() == numberLeaves)));
    }
    
    //Function to remove repeats in Vector of PhyloTree Edges. They sometimes repeat in Common Edges when they present as a split in one tree and its complement in the other. 
    private Vector<PhyloTreeEdge> RemoveRepeats(Vector<PhyloTreeEdge> vecPTE, int numberLeaves){
        Vector<PhyloTreeEdge> resVec = Tools.myVectorClonePhyloTreeEdge(vecPTE);
        
        int elementsRemoved = 0;
        for (int i = 0; i < vecPTE.size(); i++){
            for (int j = i+1; j < vecPTE.size(); j++){
                if (equivalentBip(vecPTE.get(i).getOriginalEdge().getPartition(),vecPTE.get(j).getOriginalEdge().getPartition(), numberLeaves)){
                    resVec.remove(i-elementsRemoved);
                    elementsRemoved++;
                    break;
                }
            }
        }
        
        return(resVec);
    }
    
    //Funcion to determine the edge ID on a tree, dealing with the fact that sometimes the edge will be listed as the complement of the edge in the tree. 
    private int edgeIDonT(PhyloTreeEdge e, PhyloTree T, int numberLeaves){
        int reID = T.getSplits().indexOf(e.asSplit());
        
        if (reID == -1){
            Bipartition eClone = e.getOriginalEdge().clone();
            eClone.complement(numberLeaves);
            reID = T.getSplits().indexOf(eClone);
        }
        
        return(reID);
    }
    
    //Constructor
    public OrthExtDistance(OrthExt OE1, OrthExt OE2){
        PhyloNicePrinter treePrinter = new PhyloNicePrinter();
        //We start by the starting trees in each orthant extension.
        PhyloTree T1 = new PhyloTree(OE1.getStartTree());
        PhyloTree T2 = new PhyloTree(OE2.getStartTree());
        
        
        //Find the the geodesic in between these trees. 
        Geodesic tempGeode = getGeodesic(T1, T2, null);
        
        //System.out.println("");
        //System.out.println("PROCESSING THE DISTANCE ALGORITHM... Starting at distance "+ tempGeode.getDist());
        
        //Some useful counters
        int k1 = OE1.getDim();//Dimension of the first Orthant Extension Space
        int k2 = OE2.getDim();//Dimension of the second Orthant Extension Space
        int n = OE1.getOrthantAxis().size(); //Dimension of the rows in the orthogonal matrices for both extension orthants. This should be the number of interior edges in binary trees with the complete leaf set for both, and should coincide. 
        int m1 = OE1.getFixedLengths().length;
        int m2 = OE2.getFixedLengths().length;
        
        //TO DO: add code to verify both orthant extensions are in fact inside the same BHV tree space. For now, I just assume every user will be careful about this. 
        
        
        //The following while will perform reduced gradient method algorithm, with a conjugate gradient method in each classification of variables. In each iteration the gradient of the "active variables" (those clasified into S1 and S2) function from the current trees is computed, the optimal descent direction is selected following the conjugate gradient method, and the minimum in that direction is computed. If we hit a boundary, we reclasify variables in order to increment those forced to be zero. We continue until finding a semi-stationary point, and corroborate this is the optimum or add new non-basic variables otherwise.
        
        //Initializing the indexes sets B, S and N, with some extra structures to easy change. 
        //THIS COULD POTENTIALLY BE A PART OF OrthExt class TO AVOID IT BEING COMPUTED EVERY TIME A DISTANCE IS COMPUTED
        
        Vector<Integer> B1 = new Vector<Integer>();
        Vector<Integer> B2 = new Vector<Integer>();
        
        Vector<Integer> S1 = new Vector<Integer>();
        Vector<Integer> S2 = new Vector<Integer>();
        
        Vector<Integer> N1 = new Vector<Integer>();
        Vector<Integer> N2 = new Vector<Integer>();
        
        //We will keep a vector of indexes that have already been non-basic variables, to give priority to new potential non-basic variables with possible, trying to prevent cycling. 
        Vector<Integer> alreadyN1 = new Vector<Integer>();
        Vector<Integer> alreadyN2 = new Vector<Integer>();
        
        for (int i = 0; i < m1; i++){//For each row in the map matrix
            Vector<Integer> tempVect = OE1.getMapList().get(i); //Get the edges that merge into the final edge in the original tree
            B1.add(tempVect.get(0)); //Add the first entry of this list of edges into B1
            S1.addAll(tempVect.subList(1,tempVect.size())); //The rest is added to S1
        }
        
        for (int i = 0; i < m2; i++){//For each row in the map matrix for the second extension
            Vector<Integer> tempVect = OE2.getMapList().get(i); //Get the edges that merge into the final edge in the original tree
            B2.add(tempVect.get(0)); //Add the first entry of this list of edges into B1
            S2.addAll(tempVect.subList(1,tempVect.size())); //The rest is added to S1
        }
        
        //Some values before the iterations start
        
        int iterCount = 0; //Counter of the number of iterations performed.  
        
        boolean optimNotReached = true;//We will stop the loop when the gradient is small enough to guarantee we have reach the minimum. 
        
        int conjugate_initial_counter = 0; //Counter for re-initialization of the conjugate gradient method
        
        //We need to keep track on gradients and change directions
        
        double[] gradientxs1 = new double[S1.size()];
        double[] gradientxs2 = new double[S2.size()];
        
        double[] dDirectionxs1 = new double[S1.size()];
        double[] dDirectionxs2 = new double[S2.size()];
        
        //System.out.println("ABOUT TO ENTER THE MAIN LOOP");
        //System.out.println("");
        
        while ((optimNotReached)){ // && (iterCount<50) 
            iterCount++;
            /**
            System.out.println(":::: ITERATION "+iterCount+"::::");
            System.out.println("   T1: \n" + treePrinter.toString(T1)+"\n \n");
            System.out.println("   T2: \n" + treePrinter.toString(T2)+"\n \n");
            System.out.println("   B1 = " + B1);
            System.out.println("   S1 = " + S1);
            System.out.println("   N1 = " + N1);
            System.out.println("   B2 = " + B2);
            System.out.println("   S2 = " + S2);
            System.out.println("   N2 = " + N2);
            System.out.println("");*/
            
            if (conjugate_initial_counter > S1.size() + S2.size()){
                conjugate_initial_counter = 0;
            }
            //System.out.println("Iteration number " + iterCount);
            double[] gradient1 = new double[n];
            double[] gradient2 = new double[n];
            
            RatioSequence currentRSeq = tempGeode.getRS();//The derivaties will depend on the ratio sequence in the geodesic of the geodesic between current trees T1 and T2. 
            
            //And it also depends on which common edges they have
            Vector<PhyloTreeEdge> currentECEs = tempGeode.geteCommonEdges(); 
            Vector<PhyloTreeEdge> currentFCEs = tempGeode.getfCommonEdges();
            
            //For each ratio we compute the contribution of the expression relating to the ratio in the final geodesic length in the derivative with respect to the edge. 
            Iterator<Ratio> rsIter = currentRSeq.iterator();
            while(rsIter.hasNext()){
                Ratio rat = (Ratio) rsIter.next();
                for (PhyloTreeEdge e : rat.getEEdges()){
                    //int eID = e.getOriginalID();  
                    int eID = T1.getEdges().indexOf(e);
                    if (rat.getELength() == 0){
                        gradient1[eID] += rat.getFLength();
                    } else {
                        gradient1[eID] += e.getNorm()*(1 + (rat.getFLength()/rat.getELength()));
                    }       
                }
                for (PhyloTreeEdge e : rat.getFEdges()){
                    int eID = T2.getEdges().indexOf(e);
                    if (rat.getFLength() == 0){
                        gradient2[eID] += rat.getELength();
                    } else {
                        gradient2[eID] += e.getNorm()*(1 + (rat.getELength()/rat.getFLength()));
                    }   
                }
            }
            
            //For each common edge, we compute the contribution to the derivatives in the gradient.
            
            for(PhyloTreeEdge e : currentECEs){
                int eID = T1.getEdges().indexOf(e);
                if (eID == -1){
                    continue;
                }
                EdgeAttribute T2EAtt = T2.getAttribOfSplit(e.asSplit());
                if (T2EAtt == null){
                    Bipartition eClone = e.getOriginalEdge().clone();
                    eClone.complement(OE2.getCompleteLeafSet().size());
                    T2EAtt = T2.getAttribOfSplit(eClone);
                }
                
                gradient1[eID] += (e.getNorm() - T2EAtt.norm());
            } 
            for(PhyloTreeEdge e : currentFCEs){
                int eID = T2.getEdges().indexOf(e);
                if (eID == -1){
                    continue;
                }
                EdgeAttribute T1EAtt = T1.getAttribOfSplit(e.asSplit());
                if (T1EAtt == null){
                    Bipartition eClone = e.getOriginalEdge().clone();
                    eClone.complement(OE1.getCompleteLeafSet().size());
                    T1EAtt = T1.getAttribOfSplit(eClone);
                }
                gradient2[eID] += (e.getNorm() - T1EAtt.norm());
            } 
            
            
            //Using the gradients for each "variable" (the values of the edges for each current tree) we compute the gradients of the free variables in the reduced gradient method. But first, we need to save the previous values if we are not in the first iteration of a re-initialization of the conjugate gradient method. 
            
            double[] gradientxs1Prev = gradientxs1.clone();
            double[] gradientxs2Prev = gradientxs2.clone();
            
            double akDenom = 0;
            
            if (conjugate_initial_counter > 0){
                for (int i = 0; i < gradientxs1Prev.length; i++){
                    akDenom += gradientxs1Prev[i]*gradientxs1Prev[i];
                }
                for (int i = 0; i < gradientxs2Prev.length; i++){
                    akDenom += gradientxs2Prev[i]*gradientxs2Prev[i];
                }
            }
            
            boolean gradient_small = true; // as we compute the new gradient, we assess if the size is big enough to justify another loop or we have arrive to an stationary point. 
            
            gradientxs1 = new double[S1.size()];
            gradientxs2 = new double[S2.size()];
        
            dDirectionxs1 = new double[S1.size()];
            dDirectionxs2 = new double[S2.size()];
            
            for (int i = 0; i < S1.size(); i++){
                gradientxs1[i] = gradient1[S1.get(i)] - gradient1[B1.get(OE1.getBackMap(S1.get(i)))];
                if((gradientxs1[i] < -0.00000001) || (gradientxs1[i] > 0.00000001)){
                    gradient_small = false;
                }
            }
            
            for (int i = 0; i < S2.size(); i++){
                gradientxs2[i] = gradient2[S2.get(i)] - gradient2[B2.get(OE2.getBackMap(S2.get(i)))];
                if((gradientxs2[i] < -0.00000001) || (gradientxs2[i] > 0.00000001)){
                    gradient_small = false;
                }
            }
            
            //System.out.println("The gradient for xs1 is: " + Arrays.toString(gradientxs1));
            //System.out.println("The gradient for xs2 is: " + Arrays.toString(gradientxs2));
            
            //We use continue; in case we have arrived to an stationary point in the current face being considered.
            
            if(gradient_small){
                //If the gradient is small, we have arrived to an semi-stationary point. We will check if it holds the condition to be the optimum or we need to shuffle things around to find the potential one. 
                //System.out.println("   It entered the gradient small if...");
                Vector<Integer> promisingEN1 = new Vector<Integer>();
                Vector<Integer> promisingEN2 = new Vector<Integer>();
                
                optimNotReached = false; //Assume at first that the current semi-stationary point is in fact the optimum. 
                
                for (int i = 0; i < N1.size(); i++){
                    if ((gradient1[N1.get(i)] - gradient1[B1.get(OE1.getBackMap(N1.get(i)))]) < 0){
                        promisingEN1.add(N1.get(i));
                    }
                }
                
                for (int i = 0; i < N2.size(); i++){
                    if ((gradient2[N2.get(i)] - gradient2[B2.get(OE2.getBackMap(N2.get(i)))]) < 0){
                        promisingEN2.add(N2.get(i));
                    }
                }
                
                if ((promisingEN1.size()>0) || (promisingEN2.size()>0)){
                    //System.out.println("But promising N1 is: " + promisingEN1);
                    //System.out.println("But promising N2 is: " + promisingEN2);
                    N1.removeAll(promisingEN1);
                    S1.addAll(promisingEN1);
                    
                    N2.removeAll(promisingEN2);
                    S2.addAll(promisingEN2);
                    
                    conjugate_initial_counter = 0;
                    optimNotReached = true;
                }
                
                continue;//We go back to the beginning of the loop. 
            }
            
            //We know need to determine the best direction of change depending on whether we are in the first iteration of a re=initialization of the conjutage gradient method or not
            
            if (conjugate_initial_counter == 0){
                for (int i = 0; i < dDirectionxs1.length; i++){
                    dDirectionxs1[i] = -gradientxs1[i];
                }
                for (int i = 0; i < dDirectionxs2.length; i++){
                    dDirectionxs2[i] = -gradientxs2[i];
                }
            } else {
                double akNum = 0;
                for (int i = 0; i < gradientxs1.length; i++){
                    akNum += gradientxs1[i]*(gradientxs1[i] - gradientxs1Prev[i]);
                }
                for (int i = 0; i < gradientxs2.length; i++){
                    akNum += gradientxs2[i]*(gradientxs2[i] - gradientxs2Prev[i]);
                }
                
                double ak = akNum/akDenom;
                
                for (int i = 0; i < gradientxs1.length; i++){
                    dDirectionxs1[i] = ak*dDirectionxs1[i] - gradientxs1[i];
                }
                for (int i = 0; i < gradientxs2.length; i++){
                    dDirectionxs2[i] = ak*dDirectionxs2[i] - gradientxs2[i];
                }
            }
            
            //Computing the complete change vector
            
            double[] dDirection1 = new double[n];
            
            for (int i = 0; i < S1.size(); i++){
                dDirection1[S1.get(i)] = dDirectionxs1[i];
                dDirection1[B1.get(OE1.getBackMap(S1.get(i)))] += -dDirectionxs1[i];
            }
            
            double[] dDirection2 = new double[n];
            
            for (int i = 0; i < S2.size(); i++){
                dDirection2[S2.get(i)] = dDirectionxs2[i];
                dDirection2[B2.get(OE2.getBackMap(S2.get(i)))] += -dDirectionxs2[i];
            }
            
            
            //Determining the closed set for tau, in order to mantain all edges with positive size. 
            double tau_max = 0;
            double tau_min = 0;
            boolean tauNeedsChange = true;
            
            Vector<PhyloTreeEdge> EdgesT1 = T1.getEdges();
            Vector<PhyloTreeEdge> EdgesT2 = T2.getEdges();
            
            Vector<Integer> potentialN1 = new Vector<Integer>();
            Vector<Integer> potentialN2 = new Vector<Integer>();
            
            for (int i = 0; i < EdgesT1.size(); i++){
                if (dDirection1[i] < 0){
                    if (tauNeedsChange || (-EdgesT1.get(i).getNorm()/dDirection1[i] < tau_max)){
                        tau_max = -EdgesT1.get(i).getNorm()/dDirection1[i];
                        
                        if (!N1.contains(i)){
                            potentialN1.clear();
                            potentialN1.add(i);
                        } else {
                            System.out.println("An element on N1 sneaked in (situation 1): "+ i);
                        }
                        tauNeedsChange = false;
                    } else if (-EdgesT1.get(i).getNorm()/dDirection1[i] == tau_max){
                        if (!N1.contains(i)){
                            potentialN1.add(i);
                        }else {
                            System.out.println("An element on N1 sneaked in (situation 2): "+ i);
                        }  
                    }
                }
            }
            
            for (int i = 0; i < EdgesT2.size(); i++){
                if (dDirection2[i] < 0){
                    if (tauNeedsChange || (-EdgesT2.get(i).getNorm()/dDirection2[i] < tau_max)){
                        tau_max = -EdgesT2.get(i).getNorm()/dDirection2[i];
                        
                        if (!N2.contains(i)){
                            potentialN1.clear();
                            potentialN2.clear();
                            potentialN2.add(i);
                        } else{
                            System.out.println("An element on N2 sneaked in (situation 1): "+ i);
                        }
                        tauNeedsChange = false;
                    } else if (-EdgesT2.get(i).getNorm()/dDirection2[i] == tau_max){
                        if (!N2.contains(i)){
                            potentialN2.add(i);
                        } else{
                            System.out.println("An element on N2 sneaked in (situation 2): "+ i);
                        }
                    }
                }
            }
            
            
            // We will look for the tau that minimizes f(x + tau* dDirection) between tau_min and tau_max.
            //We will first check if the minimum is the actual tau_max
            
            //Defining new values of the trees to compute geodesic and find the derivative; 
            Vector<PhyloTreeEdge> conjEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
            Vector<PhyloTreeEdge> conjEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
            
            
            //Computing the new values of the interior edges of the trees by moving in the direction of change
            for (int i = 0; i < EdgesT1.size(); i++){
                double[] tempVecEA = {EdgesT1.get(i).getNorm() + (tau_max-0.0000000000000001)*dDirection1[i]};
                EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                conjEdgesT1.get(i).setAttribute(tempEA);
            }
            
            for (int i = 0; i < EdgesT2.size(); i++){
                double[] tempVecEA = {EdgesT2.get(i).getNorm() + (tau_max-0.0000000000000001)*dDirection2[i]};
                EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                conjEdgesT2.get(i).setAttribute(tempEA);
            }
            
            PhyloTree conjT1 = new PhyloTree(conjEdgesT1, T1.getLeaf2NumMap(), T1.getLeafEdgeAttribs(), false);
            PhyloTree conjT2 = new PhyloTree(conjEdgesT2, T2.getLeaf2NumMap(), T2.getLeafEdgeAttribs(), false);
            
            //Computing geodesic in between these trees. 
            Geodesic conjGeode = getGeodesic(conjT1, conjT2, null);
            
            RatioSequence conjRSeq = conjGeode.getRS();//The derivative will depend on the ratio sequence 
            
            //And on which common edges they have
            Vector<PhyloTreeEdge> conjCEs = RemoveRepeats(conjGeode.getCommonEdges(),n+3); 
            
            
            double derivTau = 0;//Where the final derivative for tau will be saved
            
            //For each ratio we compute the contribution of the expression relating to the ratio in derivative with respect to tau_max
            Iterator<Ratio> conjRSIter = conjRSeq.iterator();
            while(conjRSIter.hasNext()){
                Ratio rat = (Ratio) conjRSIter.next();
                
                //Values that will contribute to the derivative of the ratio
                
                if (rat.getELength() > 0){
                    double ENum = 0;
                    for (PhyloTreeEdge e : rat.getEEdges()){
                        int eID = conjT1.getEdges().indexOf(e);
                        ENum += dDirection1[eID]*e.getNorm();
                    }
                    derivTau += ENum*(1 + (rat.getFLength()/rat.getELength()));
                }
                
                if (rat.getFLength() > 0){
                    double FNum = 0;
                    for (PhyloTreeEdge e : rat.getFEdges()){
                        int eID = conjT2.getEdges().indexOf(e);
                        FNum += dDirection2[eID]*e.getNorm();
                    }
                    derivTau += FNum*(1 + (rat.getELength()/rat.getFLength()));
                }
            }
            
            //For each common edge, we compute the contribution to the derivatives in the gradient. 
        
            for(PhyloTreeEdge e : conjCEs){
                int eID1 = edgeIDonT(e, conjT1, n+3);
                int eID2 = edgeIDonT(e, conjT2, n+3);
                
            
                derivTau += (dDirection1[eID1] - dDirection2[eID2])*(conjT1.getEdge(eID1).getAttribute().get(0) - conjT2.getEdge(eID2).getAttribute().get(0)); //The edge attribute in this case is the value in Tree 1 minus the value in Tree 2. 
            } 
            
            double tau = 0;
            
            if (derivTau <= 0){//In this case the minimum is reached right at the tau_max limit and the search is over.
                //System.out.println("   So it hitted a face");
                tau = tau_max;
                boolean ChangeInIndexMade = false;
                
                //We have hitted a boundary face, so we need to reclasify some variable to N1 or N2. 
                
                if (potentialN1.size() > 0){
                    //We want to give priority to indexes that have not been non-basic variables yet to try and avoid cycling. 
                    int[] IndexListOrdered = new int[potentialN1.size()];
                    int leftInd = 0;
                    int rightInd = potentialN1.size() - 1;
                    for (int i = 0; i < potentialN1.size(); i++){
                        if (alreadyN1.contains(potentialN1.get(i))){
                            IndexListOrdered[rightInd] = i;
                            rightInd--;
                        } else {
                            IndexListOrdered[leftInd] = i;
                            leftInd++;
                        }
                    }
                    for (int i : IndexListOrdered){
                        if (S1.contains(potentialN1.get(i))){
                            N1.add(potentialN1.get(i));
                            S1.remove(Integer.valueOf(potentialN1.get(i)));
                            ChangeInIndexMade = true;
                        } else if (B1.contains(potentialN1.get(i))){
                            int rowIndexTemp = B1.indexOf(potentialN1.get(i));
                            int newB1element = -1; 
                            for (int j : OE1.getMapList().get(rowIndexTemp)){
                                if (S1.contains(j)){
                                    newB1element = j;
                                    break;
                                }
                            }
                            if (newB1element == -1){
                                System.out.println("ERROR: No superbasic variable to replace the one in B1 at : " + i);
                            } else {
                                B1.set(rowIndexTemp, newB1element);
                                S1.remove(Integer.valueOf(newB1element));
                                N1.add(potentialN1.get(i));
                                ChangeInIndexMade = true;
                                break;
                            }
                        
                        }
                    }
                } else if (potentialN2.size() > 0){
                    //We want to give priority to indexes that have not been non-basic variables yet to try and avoid cycling. 
                    int[] IndexListOrdered = new int[potentialN2.size()];
                    int leftInd = 0;
                    int rightInd = potentialN2.size() - 1;
                    for (int i = 0; i < potentialN2.size(); i++){
                        if (alreadyN2.contains(potentialN2.get(i))){
                            IndexListOrdered[rightInd] = i;
                            rightInd--;
                        } else {
                            IndexListOrdered[leftInd] = i;
                            leftInd++;
                        }
                    }
                    for (int i : IndexListOrdered){
                        if (S2.contains(potentialN2.get(i))){
                            N2.add(potentialN2.get(i));
                            S2.remove(Integer.valueOf(potentialN2.get(i)));
                            ChangeInIndexMade = true;
                        } else if (B2.contains(potentialN2.get(i))){
                            int rowIndexTemp = B2.indexOf(potentialN2.get(i));
                            int newB2element = -1; 
                            for (int j : OE2.getMapList().get(rowIndexTemp)){
                                if (S2.contains(j)){
                                    newB2element = j;
                                    break;
                                }
                            }
                            if (newB2element == -1){
                                System.out.println("ERROR: No superbasic variable to replace the one in B2 at : "+ i);
                            } else {
                                B2.set(rowIndexTemp, newB2element);
                                S2.remove(Integer.valueOf(newB2element));
                                N2.add(potentialN2.get(i));
                                ChangeInIndexMade = true;
                                break;
                            }
                        }
                    }
                }
                if (!ChangeInIndexMade){
                    System.out.println("ERROR: Although a variable should be reclassified as non-basic, it did not happen.");
                    break;
                }
                
                conjugate_initial_counter = 0; // We are re-initializing the conjugate gradient method in a new face;
                
                //Defining the new trees to go back to the main while loop: 
                
                Vector<PhyloTreeEdge> newEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                Vector<PhyloTreeEdge> newEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                double[] newEdgesValuesT1 = new double[n];
                double[] newEdgesValuesT2 = new double[n];
                
                for (int i = 0; i < B1.size(); i++){
                    newEdgesValuesT1[B1.get(i)] = OE1.getFixedLengths(i);
                }
                for (int i = 0; i < S1.size(); i++){
                    newEdgesValuesT1[S1.get(i)] = EdgesT1.get(S1.get(i)).getNorm() + tau*dDirection1[S1.get(i)];
                    newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newEdgesValuesT1[S1.get(i)];
                }
                
                for (int i = 0; i < B2.size(); i++){
                    newEdgesValuesT2[B2.get(i)] = OE2.getFixedLengths(i);
                }
                for (int i = 0; i < S2.size(); i++){
                    newEdgesValuesT2[S2.get(i)] = EdgesT2.get(S2.get(i)).getNorm() + tau*dDirection2[S2.get(i)];
                    newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newEdgesValuesT2[S2.get(i)];
                }
                
                
            
                //Computing the new values of the interior edges of the trees 
                for (int i = 0; i < newEdgesT1.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT1[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT1.get(i).setAttribute(tempEA);
                }
            
                for (int i = 0; i < newEdgesT2.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT2[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT2.get(i).setAttribute(tempEA);
                }
            
                T1 = new PhyloTree(newEdgesT1, T1.getLeaf2NumMap(), T1.getLeafEdgeAttribs(), false);
                T2 = new PhyloTree(newEdgesT2, T2.getLeaf2NumMap(), T2.getLeafEdgeAttribs(), false);
                
            
                tempGeode = getGeodesic(T1, T2, null);
                
            } else {//We still need to find the optimum tau for this case. 
                //System.out.println("We are in the second option");
                int counterWhile = 0;
                tau = 0.1;
                if (tau > tau_max/2){
                    System.out.println("tau initial changed");
                    tau = tau_max/2;
                }
                
                //System.out.println("tau max: "+ tau_max);
                //System.out.println("tau min: "+ tau_min);
                //System.out.println("tau value before while: "+ tau);
                while(((derivTau < -0.0000000000000001) || (derivTau > 0.0000000000000001))){ //&&(counterWhile < 50)
                    counterWhile++;
                    //System.out.println("   Inside the tau while loop "+counterWhile);
                    tau = (tau_max + tau_min)/2;
                    
                    conjEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                    conjEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                    
                    //Computing the new values of the interior edges of the trees by moving in the direction of change
                    for (int i = 0; i < EdgesT1.size(); i++){
                        double[] tempVecEA = {EdgesT1.get(i).getNorm() + tau*dDirection1[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT1.get(i).setAttribute(tempEA);
                    }
            
                    for (int i = 0; i < EdgesT2.size(); i++){
                        double[] tempVecEA = {EdgesT2.get(i).getNorm() + tau*dDirection2[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT2.get(i).setAttribute(tempEA);
                    }
                    
                    conjT1 = new PhyloTree(conjEdgesT1, T1.getLeaf2NumMap(), T1.getLeafEdgeAttribs(), false);
                    conjT2 = new PhyloTree(conjEdgesT2, T2.getLeaf2NumMap(), T2.getLeafEdgeAttribs(), false);
                    
                    //Computing geodesic in between these trees. 
                    conjGeode = getGeodesic(conjT1, conjT2, null);
            
                    conjRSeq = conjGeode.getRS();//The derivative will depend on the ratio sequence 
            
                    //And on which common edges they have
                    conjCEs = RemoveRepeats(conjGeode.getCommonEdges(), n+3); 
            
                    derivTau = 0;//Where the final derivative for tau will be saved
                    
                    //For each ratio we compute the contribution of the expression relating to the ratio in derivative with respect to tau_max
                    conjRSIter = conjRSeq.iterator();
                    while(conjRSIter.hasNext()){
                        Ratio rat = (Ratio) conjRSIter.next();
                        
                        //Values that will contribute to the derivative of the ratio
                
                        if (rat.getELength() > 0){
                            double ENum = 0;
                            for (PhyloTreeEdge e : rat.getEEdges()){
                                int eID = conjT1.getEdges().indexOf(e);
                                ENum += dDirection1[eID]*e.getNorm();
                            }
                            derivTau += ENum*(1 + (rat.getFLength()/rat.getELength()));
                        }
                
                        if (rat.getFLength() > 0){
                            double FNum = 0;
                            for (PhyloTreeEdge e : rat.getFEdges()){
                                int eID = conjT2.getEdges().indexOf(e);
                                FNum += dDirection2[eID]*e.getNorm();
                            }
                            derivTau += FNum*(1 + (rat.getELength()/rat.getFLength()));
                        }
                    }
            
                    //For each common edge, we compute the contribution to the derivatives in the gradient. 
                    for(PhyloTreeEdge e : conjCEs){
                        int eID1 = edgeIDonT(e, conjT1, n+3); 
                        int eID2 = edgeIDonT(e, conjT2, n+3);
            
                        derivTau += (dDirection1[eID1] - dDirection2[eID2])*(conjT1.getEdge(eID1).getAttribute().get(0) - conjT2.getEdge(eID2).getAttribute().get(0)); //The edge attribute in this case is the value in Tree 1 minus the value in Tree 2. 
                    }
                    
                    if (derivTau <= 0){// This would mean the minimum is between tau and tau_max
                        tau_min = tau;
                    } else {
                        tau_max = tau;
                    }
                }
                
                conjugate_initial_counter++; // Keeping count on how many loops we have done in this face. 
                
                //Defining the new trees to go back to the main while loop: 
                
                Vector<PhyloTreeEdge> newEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                Vector<PhyloTreeEdge> newEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                
                //System.out.println("   tau after while: " + tau);
                //System.out.println("   derivTau after while: " + derivTau);
                double[] newEdgesValuesT1 = new double[n];
                double[] newEdgesValuesT2 = new double[n];
                
                for (int i = 0; i < B1.size(); i++){
                    newEdgesValuesT1[B1.get(i)] = OE1.getFixedLengths(i);
                }
                for (int i = 0; i < S1.size(); i++){
                    newEdgesValuesT1[S1.get(i)] = EdgesT1.get(S1.get(i)).getNorm() + tau*dDirection1[S1.get(i)];
                    
                    newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newEdgesValuesT1[S1.get(i)];
                }
                
                for (int i = 0; i < B2.size(); i++){
                    newEdgesValuesT2[B2.get(i)] = OE2.getFixedLengths(i);
                }
                for (int i = 0; i < S2.size(); i++){
                    newEdgesValuesT2[S2.get(i)] = EdgesT2.get(S2.get(i)).getNorm() + tau*dDirection2[S2.get(i)];
                    newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newEdgesValuesT2[S2.get(i)];
                }
                
                
            
                //Computing the new values of the interior edges of the trees 
                for (int i = 0; i < newEdgesT1.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT1[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT1.get(i).setAttribute(tempEA);
                }
            
                for (int i = 0; i < newEdgesT2.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT2[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT2.get(i).setAttribute(tempEA);
                }
            
                T1 = new PhyloTree(newEdgesT1, T1.getLeaf2NumMap(), T1.getLeafEdgeAttribs(), false);
                T2 = new PhyloTree(newEdgesT2, T2.getLeaf2NumMap(), T2.getLeafEdgeAttribs(), false);
                
                tempGeode = getGeodesic(T1, T2, null);
                
            }
            
        }
        
        //Getting the final values after the gradient descent has been performed. 
        Tree1 = T1;
        Tree2 = T2;
        FinalGeode = tempGeode;
        Distance = FinalGeode.getDist();
        
    }// end of Constructor
    
    //Second constructor that allows for restricted or unrestricted case. 
    
    //Constructor 2
    public OrthExtDistance(OrthExt OE1, OrthExt OE2, boolean restricted){
        PhyloNicePrinter treePrinter = new PhyloNicePrinter();
        //We start by the starting trees in each orthant extension.
        PhyloTree T1 = new PhyloTree(OE1.getStartTree());
        PhyloTree T2 = new PhyloTree(OE2.getStartTree());
        
        
        //Find the the geodesic in between these trees. 
        Geodesic tempGeode = getGeodesic(T1, T2, null);
        
        //System.out.println("");
        //System.out.println("PROCESSING THE DISTANCE ALGORITHM... Starting at distance "+ tempGeode.getDist());
        
        //Some useful counters
        int k1 = OE1.getDim();//Dimension of the first Orthant Extension Space
        int k2 = OE2.getDim();//Dimension of the second Orthant Extension Space
        int m1 = OE1.getFixedLengths().length;
        int m2 = OE2.getFixedLengths().length;
        
        int ol1 = 0; //Variable where the number of original leaves for the first Extension is saved in the unrestricted case.
        int ol2 = 0; //number of original leaves for the second Extension
        
        if (restricted == false){
            ol1 = OE1.getOrgLeaves2compLeaves().length;
            ol2 = OE2.getOrgLeaves2compLeaves().length;
        }
        
        int n = OE1.getOrthantAxis().size(); //The number of interior edges in binary trees with the complete leaf set and should coincide for both extension spaces. TO DO: verify it does coincide?  
        
        //TO DO: add code to verify both orthant extensions are in fact inside the same BHV tree space. For now, I just assume every user will be careful about this. 
        
        
        //The following while will perform reduced gradient method algorithm, with a conjugate gradient method in each classification of variables. In each iteration the gradient of the "active variables" (those clasified into S1 and S2) function from the current trees is computed, the optimal descent direction is selected following the conjugate gradient method, and the minimum in that direction is computed. If we hit a boundary, we reclasify variables in order to increment those forced to be zero. We continue until finding a semi-stationary point, and corroborate this is the optimum or add new non-basic variables otherwise.
        
        //Initializing the indexes sets B, S and N, with some extra structures to easy change. 
        //THIS COULD POTENTIALLY BE A PART OF OrthExt class TO AVOID IT BEING COMPUTED EVERY TIME A DISTANCE IS COMPUTED
        
        Vector<Integer> B1 = new Vector<Integer>();
        Vector<Integer> B2 = new Vector<Integer>();
        
        Vector<Integer> S1 = new Vector<Integer>();
        Vector<Integer> S2 = new Vector<Integer>();
        
        Vector<Integer> N1 = new Vector<Integer>();
        Vector<Integer> N2 = new Vector<Integer>();
        
        //We will keep a vector of indexes that have already been non-basic variables, to give priority to new potential non-basic variables with possible, trying to prevent cycling. 
        Vector<Integer> alreadyN1 = new Vector<Integer>();
        Vector<Integer> alreadyN2 = new Vector<Integer>();
        
        for (int i = 0; i < m1; i++){//For each row in the map matrix
            Vector<Integer> tempVect = OE1.getMapList().get(i); //Get the edges that merge into the final edge in the original tree
            B1.add(tempVect.get(0)); //Add the first entry of this list of edges into B1
            S1.addAll(tempVect.subList(1,tempVect.size())); //The rest is added to S1
        }
        
        for (int i = 0; i < m2; i++){//For each row in the map matrix for the second extension
            Vector<Integer> tempVect = OE2.getMapList().get(i); //Get the edges that merge into the final edge in the original tree
            B2.add(tempVect.get(0)); //Add the first entry of this list of edges into B1
            S2.addAll(tempVect.subList(1,tempVect.size())); //The rest is added to S1
        }
        
        //Some values before the iterations start
        
        int iterCount = 0; //Counter of the number of iterations performed.  
        
        boolean optimNotReached = true;//We will stop the loop when the gradient is small enough to guarantee we have reach the minimum. 
        
        int conjugate_initial_counter = 0; //Counter for re-initialization of the conjugate gradient method
        
        //We need to keep track on gradients and change directions
        
        double[] gradientxs1 = new double[S1.size()];
        double[] gradientxs2 = new double[S2.size()];
        
        double[] dDirectionxs1 = new double[S1.size()];
        double[] dDirectionxs2 = new double[S2.size()];
        
        //System.out.println("ABOUT TO ENTER THE MAIN LOOP");
        //System.out.println("");
        
        while ((optimNotReached)){ // && (iterCount<2)
            iterCount++;
            
            /**System.out.println(":::: ITERATION "+iterCount+"::::");
            System.out.println("   T1: \n" + treePrinter.toString(T1)+"\n \n");
            System.out.println("   T2: \n" + treePrinter.toString(T2)+"\n \n");
            System.out.println("   B1 = " + B1);
            System.out.println("   S1 = " + S1);
            System.out.println("   N1 = " + N1);
            System.out.println("   B2 = " + B2);
            System.out.println("   S2 = " + S2);
            System.out.println("   N2 = " + N2);
            System.out.println("");*/
            
            if (conjugate_initial_counter > S1.size() + S2.size()){
                conjugate_initial_counter = 0;
            }
            //System.out.println("Iteration number " + iterCount);
            double[] gradient1 = new double[n + ol1];
            double[] gradient2 = new double[n + ol2];
            
            RatioSequence currentRSeq = tempGeode.getRS();//The derivaties will depend on the ratio sequence in the geodesic of the geodesic between current trees T1 and T2. 
            
            //And it also depends on which common edges they have
            Vector<PhyloTreeEdge> currentECEs = tempGeode.geteCommonEdges(); 
            Vector<PhyloTreeEdge> currentFCEs = tempGeode.getfCommonEdges();
            
            //For each ratio we compute the contribution of the expression relating to the ratio in the final geodesic length in the derivative with respect to the edge. 
            Iterator<Ratio> rsIter = currentRSeq.iterator();
            while(rsIter.hasNext()){
                Ratio rat = (Ratio) rsIter.next();
                for (PhyloTreeEdge e : rat.getEEdges()){
                    //int eID = e.getOriginalID();  
                    int eID = T1.getEdges().indexOf(e);
                    if (rat.getELength() == 0){
                        gradient1[eID + ol1] += rat.getFLength(); //We add ol1 since in the unrestricted case, all internal edges are pushed to the end, because the original leave edges are all first. 
                    } else {
                        gradient1[eID + ol1] += e.getNorm()*(1 + (rat.getFLength()/rat.getELength()));
                    }       
                }
                for (PhyloTreeEdge e : rat.getFEdges()){
                    int eID = T2.getEdges().indexOf(e);
                    if (rat.getFLength() == 0){
                        gradient2[eID + ol2] += rat.getELength();
                    } else {
                        gradient2[eID + ol2] += e.getNorm()*(1 + (rat.getELength()/rat.getFLength()));
                    }   
                }
            }
            
            //For each common edge, we compute the contribution to the derivatives in the gradient.
            
            for(PhyloTreeEdge e : currentECEs){
                int eID = T1.getEdges().indexOf(e);
                if (eID == -1){
                    continue;
                }
                EdgeAttribute T2EAtt = T2.getAttribOfSplit(e.asSplit());
                if (T2EAtt == null){
                    Bipartition eClone = e.getOriginalEdge().clone();
                    eClone.complement(OE2.getCompleteLeafSet().size());
                    T2EAtt = T2.getAttribOfSplit(eClone);
                }
                
                gradient1[eID + ol1] += (e.getNorm() - T2EAtt.norm());
            } 
            for(PhyloTreeEdge e : currentFCEs){
                int eID = T2.getEdges().indexOf(e);
                if (eID == -1){
                    continue;
                }
                EdgeAttribute T1EAtt = T1.getAttribOfSplit(e.asSplit());
                if (T1EAtt == null){
                    Bipartition eClone = e.getOriginalEdge().clone();
                    eClone.complement(OE1.getCompleteLeafSet().size());
                    T1EAtt = T1.getAttribOfSplit(eClone);
                }
                gradient2[eID + ol2] += (e.getNorm() - T1EAtt.norm());
            } 
            
            
            //In the unrestricted case, lenghts of external edges to the original leaves are also potential variables, and are treated similarly to common edges. 
            if (restricted){
                for (int i = 0; i < ol1; i++){
                    gradient1[i] += (T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(i)].get(0) - T2.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(i)].get(0));
                }
                for (int i = 0; i < ol2; i++){
                    gradient2[i] += (T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(i)].get(0) - T1.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(i)].get(0));
                }
            }
            
            
            //Using the gradients for each "variable" (the values of the edges for each current tree) we compute the gradients of the free variables in the reduced gradient method. But first, we need to save the previous values if we are not in the first iteration of a re-initialization of the conjugate gradient method. 
            
            double[] gradientxs1Prev = gradientxs1.clone();
            double[] gradientxs2Prev = gradientxs2.clone();
            double akDenom = 0;
            
            if (conjugate_initial_counter > 0){
                for (int i = 0; i < gradientxs1Prev.length; i++){
                    akDenom += gradientxs1Prev[i]*gradientxs1Prev[i];
                }
                for (int i = 0; i < gradientxs2Prev.length; i++){
                    akDenom += gradientxs2Prev[i]*gradientxs2Prev[i];
                }
            }
            
             boolean gradient_small = true; // as we compute the new gradient, we assess if the size is big enough to justify another loop or we have arrive to an stationary point. 
            
            for (int i = 0; i < S1.size(); i++){
                gradientxs1[i] = gradient1[S1.get(i)] - gradient1[B1.get(OE1.getBackMap(S1.get(i)))];
                if((gradientxs1[i] < -0.0000000001) || (gradientxs1[i] > 0.0000000001)){
                    gradient_small = false;
                }
            }
            
            for (int i = 0; i < S2.size(); i++){
                gradientxs2[i] = gradient2[S2.get(i)] - gradient2[B2.get(OE2.getBackMap(S2.get(i)))];
                if((gradientxs2[i] < -0.0000000001) || (gradientxs2[i] > 0.0000000001)){
                    gradient_small = false;
                }
            }

            
            
            
            //We use continue; in case we have arrived to an stationary point in the current face being considered.
            
            if(gradient_small){
                //If the gradient is small, we have arrived to an semi-stationary point. We will check if it holds the condition to be the optimum or we need to shuffle things around to find the potential one. 
                //System.out.println("   It entered the gradient small if...");
                Vector<Integer> promisingEN1 = new Vector<Integer>();
                Vector<Integer> promisingEN2 = new Vector<Integer>();
                
                optimNotReached = false; //Assume at first that the current semi-stationary point is in fact the optimum. 
                
                for (int i = 0; i < N1.size(); i++){
                    if ((gradient1[N1.get(i)] - gradient1[B1.get(OE1.getBackMap(N1.get(i)))]) < 0){
                        promisingEN1.add(N1.get(i));
                    }
                }
                
                for (int i = 0; i < N2.size(); i++){
                    if ((gradient2[N2.get(i)] - gradient2[B2.get(OE2.getBackMap(N2.get(i)))]) < 0){
                        promisingEN2.add(N2.get(i));
                    }
                }
                
                if ((promisingEN1.size()>0) || (promisingEN2.size()>0)){
                    N1.removeAll(promisingEN1);
                    S1.addAll(promisingEN1);
                    
                    N2.removeAll(promisingEN2);
                    S2.addAll(promisingEN2);
                    
                    conjugate_initial_counter = 0;
                    optimNotReached = true;
                    
                }
                
                continue;//We go back to the beginning of the loop. 
            }
            
            //We know need to determine the best direction of change depending on whether we are in the first iteration of a re=initialization of the conjutage gradient method or not
            
            if (conjugate_initial_counter == 0){
                for (int i = 0; i < dDirectionxs1.length; i++){
                    dDirectionxs1[i] = -gradientxs1[i];
                }
                for (int i = 0; i < dDirectionxs2.length; i++){
                    dDirectionxs2[i] = -gradientxs2[i];
                }
            } else {
                double akNum = 0;
                for (int i = 0; i < gradientxs1.length; i++){
                    akNum += gradientxs1[i]*(gradientxs1[i] - gradientxs1Prev[i]);
                }
                for (int i = 0; i < gradientxs2.length; i++){
                    akNum += gradientxs2[i]*(gradientxs2[i] - gradientxs2Prev[i]);
                }
                
                double ak = akNum/akDenom;
                
                for (int i = 0; i < gradientxs1.length; i++){
                    dDirectionxs1[i] = ak*dDirectionxs1[i] - gradientxs1[i];
                }
                for (int i = 0; i < gradientxs2.length; i++){
                    dDirectionxs2[i] = ak*dDirectionxs2[i] - gradientxs2[i];
                }
            }
            
            //Computing the complete change vector
            
            double[] dDirection1 = new double[n + ol1];
            
            for (int i = 0; i < S1.size(); i++){
                dDirection1[S1.get(i)] = dDirectionxs1[i];
                dDirection1[B1.get(OE1.getBackMap(S1.get(i)))] += -dDirectionxs1[i];
            }
            
            double[] dDirection2 = new double[n + ol2];
            
            for (int i = 0; i < S2.size(); i++){
                dDirection2[S2.get(i)] = dDirectionxs2[i];
                dDirection2[B2.get(OE2.getBackMap(S2.get(i)))] += -dDirectionxs2[i];
            }
            
            //Determining the closed set for tau, in order to mantain all edges with positive size. 
            double tau_max = 0;
            double tau_min = 0;
            boolean tauNeedsChange = true;
            
            Vector<PhyloTreeEdge> EdgesT1 = T1.getEdges();
            Vector<PhyloTreeEdge> EdgesT2 = T2.getEdges();
            
            Vector<Integer> potentialN1 = new Vector<Integer>();
            Vector<Integer> potentialN2 = new Vector<Integer>();
            
            //In the unrestricted case, we need to check the external edges in the original tree to find tau_max as well
            if (!restricted){
                for (int i = 0; i < ol1; i++){
                    if (dDirection1[i] < 0){
                        if(tauNeedsChange || (-T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(i)].get(0)/dDirection1[i] < tau_max)){
                            tau_max = -T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(i)].get(0)/dDirection1[i];
                            if (!N1.contains(i)){
                                potentialN1.clear();
                                potentialN1.add(i);
                            } else {
                                System.out.println("An element on N1 sneaked in (situation 1): "+ i);
                            }
                            tauNeedsChange = false;
                        } else if(-T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(i)].get(0)/dDirection1[i] == tau_max){
                            if (!N1.contains(i)){
                                potentialN1.add(i);
                            }else {
                                System.out.println("An element on N1 sneaked in (situation 2): "+ i);
                            } 
                        }
                    }
                }
                for (int i = 0; i < ol2; i++){
                    if (dDirection2[i] < 0){
                        if(tauNeedsChange || (-T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(i)].get(0)/dDirection2[i] < tau_max)){
                            tau_max = -T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(i)].get(0)/dDirection2[i];
                            if (!N2.contains(i)){
                                potentialN2.clear();
                                potentialN2.add(i);
                            } else {
                                System.out.println("An element on N2 sneaked in (situation 1): "+ i);
                            }
                            tauNeedsChange = false;
                        } else if(-T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(i)].get(0)/dDirection2[i] == tau_max){
                            if (!N2.contains(i)){
                                potentialN2.add(i);
                            }else {
                                System.out.println("An element on N2 sneaked in (situation 2): "+ i);
                            } 
                        }
                    }
                }
            }
            
            for (int i = 0; i < EdgesT1.size(); i++){
                if (dDirection1[i + ol1] < 0){
                    if(tauNeedsChange || (-EdgesT1.get(i).getNorm()/dDirection1[i + ol1] < tau_max)){
                        tau_max = -EdgesT1.get(i).getNorm()/dDirection1[i + ol1];
                        if (!N1.contains(i+ol1)){
                            potentialN1.clear();
                            potentialN1.add(i+ol1);
                        } else {
                            System.out.println("An element on N1 sneaked in (situation 1): "+ (i+ol1));
                        }
                        tauNeedsChange = false;
                    } else if (-EdgesT1.get(i).getNorm()/dDirection1[i+ol1] == tau_max){
                        if (!N1.contains(i+ol1)){
                            potentialN1.add(i+ol1);
                        }else {
                            System.out.println("An element on N1 sneaked in (situation 2): "+ (i+ol1));
                        }  
                    }
                }
            }
            
            for (int i = 0; i < EdgesT2.size(); i++){
                if (dDirection2[i + ol2] < 0){
                    if (tauNeedsChange || (-EdgesT2.get(i).getNorm()/dDirection2[i + ol2] < tau_max)){
                        tau_max = -EdgesT2.get(i).getNorm()/dDirection2[i + ol2];
                        if (!N2.contains(i + ol2)){
                            potentialN1.clear();
                            potentialN2.clear();
                            potentialN2.add(i + ol2);
                        } else{
                            System.out.println("An element on N2 sneaked in (situation 1): "+ (i + ol2));
                        }
                        tauNeedsChange = false;
                    } else if (-EdgesT2.get(i).getNorm()/dDirection2[i] == tau_max){
                        if (!N2.contains(i + ol2)){
                            potentialN2.add(i + ol2);
                        } else{
                            System.out.println("An element on N2 sneaked in (situation 2): "+ (i + ol2));
                        }
                    }
                }
            }
            
            // We will look for the tau that minimizes f(x + tau* dDirection) between tau_min and tau_max.
            //We will first check if the minimum is the actual tau_max
            
            //Defining new values of the trees to compute geodesic and find the derivative; 
            Vector<PhyloTreeEdge> conjEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
            Vector<PhyloTreeEdge> conjEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
            
            
            //Computing the new values of the interior edges of the trees by moving in the direction of change
            for (int i = 0; i < EdgesT1.size(); i++){
                double[] tempVecEA = {EdgesT1.get(i).getNorm() + (tau_max-0.0000000000001)*dDirection1[i+ol1]};
                EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                conjEdgesT1.get(i).setAttribute(tempEA);
            }
            
            for (int i = 0; i < EdgesT2.size(); i++){
                double[] tempVecEA = {EdgesT2.get(i).getNorm() + (tau_max-0.0000000000001)*dDirection2[i+ol2]};
                EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                conjEdgesT2.get(i).setAttribute(tempEA);
            }
            
            //We also need to change the values in the Leaf Edge attribs in the unrestricted case. 
            
            EdgeAttribute[] T1LeafEdgeAtt = T1.getCopyLeafEdgeAttribs();
            EdgeAttribute[] T2LeafEdgeAtt = T2.getCopyLeafEdgeAttribs();
            
            if (!restricted){
                for (int i = 0; i < ol1; i++){
                    double[] tempVecEA = {T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0) + (tau_max-0.0000000000001)*dDirection1[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                }
                for (int i = 0; i < ol2; i++){
                    double[] tempVecEA = {T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0) + (tau_max-0.0000000000001)*dDirection2[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                }
            }
            
            PhyloTree conjT1 = new PhyloTree(conjEdgesT1, T1.getLeaf2NumMap(), T1LeafEdgeAtt, false);
            PhyloTree conjT2 = new PhyloTree(conjEdgesT2, T2.getLeaf2NumMap(), T2LeafEdgeAtt, false);
            
            //Computing geodesic in between these trees. 
            Geodesic conjGeode = getGeodesic(conjT1, conjT2, null);
            
            RatioSequence conjRSeq = conjGeode.getRS();//The derivative will depend on the ratio sequence 
            
            //And on which common edges they have
            Vector<PhyloTreeEdge> conjCEs = conjGeode.getCommonEdges(); 
            
            double derivTau = 0;//Where the final derivative for tau will be saved
            
            //For each ratio we compute the contribution of the expression relating to the ratio in derivative with respect to tau_max
            Iterator<Ratio> conjRSIter = conjRSeq.iterator();
            while(conjRSIter.hasNext()){
                Ratio rat = (Ratio) conjRSIter.next();
                //Values that will contribute to the derivative of the ratio
                
                if (rat.getELength() > 0){
                    double ENum = 0;
                    for (PhyloTreeEdge e : rat.getEEdges()){
                        int eID = conjT1.getEdges().indexOf(e);
                        ENum += dDirection1[eID + ol1]*e.getNorm();
                    }
                    derivTau += ENum*(1 + (rat.getFLength()/rat.getELength()));
                }
                
                if (rat.getFLength() > 0){
                    double FNum = 0;
                    for (PhyloTreeEdge e : rat.getFEdges()){
                        int eID = conjT2.getEdges().indexOf(e);
                        FNum += dDirection2[eID + ol2]*e.getNorm();
                    }
                    derivTau += FNum*(1 + (rat.getELength()/rat.getFLength()));
                }
            }
            
            //For each common edge, we compute the contribution to the derivatives in the gradient. 
            
            Vector<PhyloTreeEdge> conjCEsReduced = RemoveRepeats(conjCEs, n+3);
            
            for(PhyloTreeEdge e : conjCEsReduced){
                int eID1 = edgeIDonT(e, conjT1, n+3); 
                int eID2 = edgeIDonT(e, conjT2, n+3); 
                
                if (eID1 == -1){
                    System.out.println("Warning 1.1: " + e);
                }
                if (eID2 == -1){
                    System.out.println("Warning 1.2: " + e);
                }
                
                derivTau += (dDirection1[eID1 + ol1] - dDirection2[eID2 + ol2])*(conjT1.getEdge(eID1).getAttribute().get(0) - conjT2.getEdge(eID2).getAttribute().get(0)); //The edge attribute in this case is the value in Tree 1 minus the value in Tree 2. 
            } 
            
            //In the unrestricted case, we need to also consider the contribution of the external edge to the gradient. 
            
            if (!restricted){
                for (int i = 0; i < ol1; i++){
                    derivTau += dDirection1[i]*(T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0) - T2LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0));
                }
                for (int i = 0; i < ol2; i++){
                    derivTau += dDirection2[i]*(T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0) - T1LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0));
                }
            }
            
            double tau = 0;
            
            System.out.println("   Tau derivative for tau_max ended being "+ derivTau);
            
            if (derivTau <= 0){//In this case the minimum is reached right at the tau_max limit and the search is over.
                System.out.println("   So it hitted a face");
                tau = tau_max;
                boolean ChangeInIndexMade = false;
                
                //We have hitted a boundary face, so we need to reclasify some variable to N1 or N2. 
                
                if (potentialN1.size() > 0){
                    //We want to give priority to indexes that have not been non-basic variables yet to try and avoid cycling. 
                    int[] IndexListOrdered = new int[potentialN1.size()];
                    int leftInd = 0;
                    int rightInd = potentialN1.size() - 1;
                    for (int i = 0; i < potentialN1.size(); i++){
                        if (alreadyN1.contains(potentialN1.get(i))){
                            IndexListOrdered[rightInd] = i;
                            rightInd--;
                        } else {
                            IndexListOrdered[leftInd] = i;
                            leftInd++;
                        }
                    }
                    for (int i : IndexListOrdered){
                        if (S1.contains(potentialN1.get(i))){
                            N1.add(potentialN1.get(i));
                            S1.remove(Integer.valueOf(potentialN1.get(i)));
                            ChangeInIndexMade = true;
                        } else if (B1.contains(potentialN1.get(i))){
                            int rowIndexTemp = B1.indexOf(potentialN1.get(i));
                            int newB1element = -1; 
                            for (int j : OE1.getMapList().get(rowIndexTemp)){
                                if (S1.contains(j)){
                                    newB1element = j;
                                    break;
                                }
                            }
                            if (newB1element == -1){
                                System.out.println("ERROR: No superbasic variable to replace the one in B1 at : " + i);
                            } else {
                                B1.set(rowIndexTemp, newB1element);
                                S1.remove(Integer.valueOf(newB1element));
                                N1.add(potentialN1.get(i));
                                ChangeInIndexMade = true;
                                break;
                            }
                        
                        }
                    }
                } else if (potentialN2.size() > 0){
                    //We want to give priority to indexes that have not been non-basic variables yet to try and avoid cycling. 
                    int[] IndexListOrdered = new int[potentialN2.size()];
                    int leftInd = 0;
                    int rightInd = potentialN2.size() - 1;
                    for (int i = 0; i < potentialN2.size(); i++){
                        if (alreadyN2.contains(potentialN2.get(i))){
                            IndexListOrdered[rightInd] = i;
                            rightInd--;
                        } else {
                            IndexListOrdered[leftInd] = i;
                            leftInd++;
                        }
                    }
                    for (int i : IndexListOrdered){
                        if (S2.contains(potentialN2.get(i))){
                            N2.add(potentialN2.get(i));
                            S2.remove(Integer.valueOf(potentialN2.get(i)));
                            ChangeInIndexMade = true;
                        } else if (B2.contains(potentialN2.get(i))){
                            int rowIndexTemp = B2.indexOf(potentialN2.get(i));
                            int newB2element = -1; 
                            for (int j : OE2.getMapList().get(rowIndexTemp)){
                                if (S2.contains(j)){
                                    newB2element = j;
                                    break;
                                }
                            }
                            if (newB2element == -1){
                                System.out.println("ERROR: No superbasic variable to replace the one in B2 at : "+ i);
                            } else {
                                B2.set(rowIndexTemp, newB2element);
                                S2.remove(Integer.valueOf(newB2element));
                                N2.add(potentialN2.get(i));
                                ChangeInIndexMade = true;
                                break;
                            }
                        }
                    }
                }
                if (!ChangeInIndexMade){
                    System.out.println("ERROR: Although a variable should be reclassified as non-basic, it did not happen.");
                    break;
                }
                
                conjugate_initial_counter = 0; // We are re-initializing the conjugate gradient method in a new face;
                
                //Defining the new trees to go back to the main while loop: 
                
                /**System.out.println("    Just before new trees definition tau is " + tau);
                
                System.out.println("    The edges of T1 are " + EdgesT1);
                
                System.out.println("    The edges of T2 are " + EdgesT2);
                
                System.out.println("    S1 = " + S1);
                System.out.println("    B1 = " + B1);
                System.out.println("    S2 = " + S2);
                System.out.println("    B2 = " + B2);
                
                System.out.println("    And the directions are " + Arrays.toString(dDirection1) + " and " + Arrays.toString(dDirection2));*/
                
                Vector<PhyloTreeEdge> newEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                Vector<PhyloTreeEdge> newEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                double[] newEdgesValuesT1 = new double[n];
                double[] newEdgesValuesT2 = new double[n];
                double[] newLeafEdgesvaluesT1 = new double[ol1];
                double[] newLeafEdgesvaluesT2 = new double[ol2];
                
                for (int i = 0; i < B1.size(); i++){
                    if (B1.get(i) < ol1){
                        newLeafEdgesvaluesT1[B1.get(i)] = OE1.getFixedLengths(i);
                    } else {
                        newEdgesValuesT1[B1.get(i) - ol1] = OE1.getFixedLengths(i);
                    }
                }
                for (int i = 0; i < S1.size(); i++){
                    if (S1.get(i) < ol1){
                        newLeafEdgesvaluesT1[S1.get(i)] = T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(S1.get(i))].get(0) + tau*dDirection1[S1.get(i)];
                        if (B1.get(OE1.getBackMap(S1.get(i))) < ol1){
                            newLeafEdgesvaluesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newLeafEdgesvaluesT1[S1.get(i)];
                        } else {
                            newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i))) - ol1] -= newLeafEdgesvaluesT1[S1.get(i)];
                        }
                    } else {
                        newEdgesValuesT1[S1.get(i) - ol1] = EdgesT1.get(S1.get(i)-ol1).getNorm() + tau*dDirection1[S1.get(i)];
                        if (B1.get(OE1.getBackMap(S1.get(i))) < ol1){
                            newLeafEdgesvaluesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newEdgesValuesT1[S1.get(i) - ol1];
                        } else {
                            newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i))) - ol1] -= newEdgesValuesT1[S1.get(i) - ol1];
                        }
                    }
                }
                
                for (int i = 0; i < B2.size(); i++){
                    if (B2.get(i) < ol2){
                        newLeafEdgesvaluesT2[B2.get(i)] = OE2.getFixedLengths(i);
                    } else {
                        newEdgesValuesT2[B2.get(i) - ol2] = OE2.getFixedLengths(i);
                    }
                }
                for (int i = 0; i < S2.size(); i++){
                    if (S2.get(i) < ol2){
                        newLeafEdgesvaluesT2[S2.get(i)] = T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(S2.get(i))].get(0) + tau*dDirection2[S2.get(i)];
                        if (B2.get(OE2.getBackMap(S2.get(i))) < ol2){
                            newLeafEdgesvaluesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newLeafEdgesvaluesT2[S2.get(i)];
                        } else {
                            newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i))) - ol2] -= newLeafEdgesvaluesT2[S2.get(i)];
                        }
                    } else {
                        newEdgesValuesT2[S2.get(i) - ol2] = EdgesT2.get(S2.get(i)-ol2).getNorm() + tau*dDirection2[S2.get(i)];
                        if (B2.get(OE2.getBackMap(S2.get(i))) < ol2){
                            newLeafEdgesvaluesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newEdgesValuesT2[S2.get(i) - ol2];
                        } else {
                            newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i))) - ol2] -= newEdgesValuesT2[S2.get(i) - ol2];
                        }
                    }
                }
                
                
            
                //Computing the new values of the interior edges of the trees 
                for (int i = 0; i < newEdgesT1.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT1[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT1.get(i).setAttribute(tempEA);
                }
            
                for (int i = 0; i < newEdgesT2.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT2[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT2.get(i).setAttribute(tempEA);
                }
                
                //We also need to change the values in the Leaf Edge attribs in the unrestricted case. 
            
                EdgeAttribute[] newT1LeafEdgeAtt = T1.getCopyLeafEdgeAttribs();
                EdgeAttribute[] newT2LeafEdgeAtt = T2.getCopyLeafEdgeAttribs();
            
                if (!restricted){
                    for (int i = 0; i < ol1; i++){
                        double[] tempVecEA = {newLeafEdgesvaluesT1[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        newT1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                    }
                    for (int i = 0; i < ol2; i++){
                        double[] tempVecEA = {newLeafEdgesvaluesT2[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        newT2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                    }
                }
            
            
                T1 = new PhyloTree(newEdgesT1, T1.getLeaf2NumMap(), newT1LeafEdgeAtt, false);
                T2 = new PhyloTree(newEdgesT2, T2.getLeaf2NumMap(), newT2LeafEdgeAtt, false);
            
                tempGeode = getGeodesic(T1, T2, null);
                
            } else {//We still need to find the optimum tau for this case. 
                //System.out.println("   So we are still in the same face");
                int counterWhile = 0;
                tau = 0.1;
                if (tau > tau_max/2){
                    tau = tau_max/2;
                }
                //System.out.println("    Prev tau = " + tau);
                while(((derivTau < -0.000000000001) || (derivTau > 0.000000000001))){ //&&(counterWhile < 50)
                    counterWhile++;
                    //System.out.println("   Inside the tau while loop "+counterWhile);
                    tau = (tau_max + tau_min)/2;
                    conjEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                    conjEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                    
                    //Computing the new values of the interior edges of the trees by moving in the direction of change
                    for (int i = 0; i < EdgesT1.size(); i++){
                        double[] tempVecEA = {EdgesT1.get(i).getNorm() + tau*dDirection1[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT1.get(i).setAttribute(tempEA);
                    }
            
                    for (int i = 0; i < EdgesT2.size(); i++){
                        double[] tempVecEA = {EdgesT2.get(i).getNorm() + tau*dDirection2[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT2.get(i).setAttribute(tempEA);
                    }
                    //////////////////////
                    
                    conjEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                    conjEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
            
            
                    //Computing the new values of the interior edges of the trees by moving in the direction of change
                    for (int i = 0; i < EdgesT1.size(); i++){
                        double[] tempVecEA = {EdgesT1.get(i).getNorm() + (tau)*dDirection1[i+ol1]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT1.get(i).setAttribute(tempEA);
                    }
            
                    for (int i = 0; i < EdgesT2.size(); i++){
                        double[] tempVecEA = {EdgesT2.get(i).getNorm() + (tau)*dDirection2[i+ol2]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        conjEdgesT2.get(i).setAttribute(tempEA);
                    }
            
                    //We also need to change the values in the Leaf Edge attribs in the unrestricted case. 
            
                    T1LeafEdgeAtt = T1.getCopyLeafEdgeAttribs();
                    T2LeafEdgeAtt = T2.getCopyLeafEdgeAttribs();
            
                    if (!restricted){
                        for (int i = 0; i < ol1; i++){
                            double[] tempVecEA = {T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0) + tau*dDirection1[i]};
                            EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                            T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                        }
                        for (int i = 0; i < ol2; i++){
                            double[] tempVecEA = {T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0) + tau*dDirection2[i]};
                            EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                            T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                        }
                    }
            
                    
                    conjT1 = new PhyloTree(conjEdgesT1, T1.getLeaf2NumMap(), T1LeafEdgeAtt, false);
                    conjT2 = new PhyloTree(conjEdgesT2, T2.getLeaf2NumMap(), T2LeafEdgeAtt, false);
                    
                    //Computing geodesic in between these trees. 
                    conjGeode = getGeodesic(conjT1, conjT2, null);
            
                    conjRSeq = conjGeode.getRS();//The derivative will depend on the ratio sequence 
            
                    //And on which common edges they have
                    conjCEs = conjGeode.getCommonEdges(); 
            
                    derivTau = 0;//Where the final derivative for tau will be saved
                    
                    //For each ratio we compute the contribution of the expression relating to the ratio in derivative with respect to tau_max
                    conjRSIter = conjRSeq.iterator();
                    while(conjRSIter.hasNext()){
                        Ratio rat = (Ratio) conjRSIter.next();
                        //Values that will contribute to the derivative of the ratio
                
                        if (rat.getELength() > 0){
                            double ENum = 0;
                            for (PhyloTreeEdge e : rat.getEEdges()){
                                int eID = conjT1.getEdges().indexOf(e);
                                ENum += dDirection1[eID + ol1]*e.getNorm();
                            }
                            derivTau += ENum*(1 + (rat.getFLength()/rat.getELength()));
                        }
                
                        if (rat.getFLength() > 0){
                            double FNum = 0;
                            for (PhyloTreeEdge e : rat.getFEdges()){
                                int eID = conjT2.getEdges().indexOf(e);
                                FNum += dDirection2[eID + ol2]*e.getNorm();
                            }
                            derivTau += FNum*(1 + (rat.getELength()/rat.getFLength()));
                        }
                    }
            
                    //For each common edge, we compute the contribution to the derivatives in the gradient. 
                    for(PhyloTreeEdge e : conjCEs){
                        int eID1 = edgeIDonT(e, conjT1, n+3);
                        int eID2 = edgeIDonT(e, conjT2, n+3);
                        
            
                        derivTau += (dDirection1[eID1 + ol1] - dDirection2[eID2 + ol2])*(conjT1.getEdge(eID1).getAttribute().get(0) - conjT2.getEdge(eID2).getAttribute().get(0)); //The edge attribute in this case is the value in Tree 1 minus the value in Tree 2. 
                    }
                    
                    //In the unrestricted case, we need to also consider the contribution of the external edge to the gradient. 
            
                    if (!restricted){
                        for (int i = 0; i < ol1; i++){
                            derivTau += dDirection1[i]*(T1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0) - T2LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].get(0));
                        }
                        for (int i = 0; i < ol2; i++){
                            derivTau += dDirection2[i]*(T2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0) - T1LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].get(0));
                        }
                    }
                    
                    if (derivTau <= 0){// This would mean the minimum is between tau and tau_max
                        tau_min = tau;
                    } else {
                        tau_max = tau;
                    }
                }
                
                conjugate_initial_counter++; // Keeping count on how many loops we have done in this face. 
                
                //Defining the new trees to go back to the main while loop: 
                
                
                Vector<PhyloTreeEdge> newEdgesT1 = Tools.myVectorClonePhyloTreeEdge(EdgesT1);
                Vector<PhyloTreeEdge> newEdgesT2 = Tools.myVectorClonePhyloTreeEdge(EdgesT2);
                double[] newEdgesValuesT1 = new double[n];
                double[] newEdgesValuesT2 = new double[n];
                double[] newLeafEdgesvaluesT1 = new double[ol1];
                double[] newLeafEdgesvaluesT2 = new double[ol2];
                
                for (int i = 0; i < B1.size(); i++){
                    if (B1.get(i) < ol1){
                        newLeafEdgesvaluesT1[B1.get(i)] = OE1.getFixedLengths(i);
                    } else {
                        newEdgesValuesT1[B1.get(i) - ol1] = OE1.getFixedLengths(i);
                    }
                }
                for (int i = 0; i < S1.size(); i++){
                    if (S1.get(i) < ol1){
                        newLeafEdgesvaluesT1[S1.get(i)] = T1.getLeafEdgeAttribs()[OE1.getOrgLeaves2compLeaves(S1.get(i))].get(0) + tau*dDirection1[S1.get(i)];
                        if (B1.get(OE1.getBackMap(S1.get(i))) < ol1){
                            newLeafEdgesvaluesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newLeafEdgesvaluesT1[S1.get(i)];
                        } else {
                            newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i))) - ol1] -= newLeafEdgesvaluesT1[S1.get(i)];
                        }
                    } else {
                        newEdgesValuesT1[S1.get(i) - ol1] = EdgesT1.get(S1.get(i)-ol1).getNorm() + tau*dDirection1[S1.get(i)];
                        if (B1.get(OE1.getBackMap(S1.get(i))) < ol1){
                            newLeafEdgesvaluesT1[B1.get(OE1.getBackMap(S1.get(i)))] -= newEdgesValuesT1[S1.get(i) - ol1];
                        } else {
                            newEdgesValuesT1[B1.get(OE1.getBackMap(S1.get(i))) - ol1] -= newEdgesValuesT1[S1.get(i) - ol1];
                        }
                    }
                }
                
                for (int i = 0; i < B2.size(); i++){
                    if (B2.get(i) < ol2){
                        newLeafEdgesvaluesT2[B2.get(i)] = OE2.getFixedLengths(i);
                    } else {
                        newEdgesValuesT2[B2.get(i) - ol2] = OE2.getFixedLengths(i);
                    }
                }
                for (int i = 0; i < S2.size(); i++){
                    if (S2.get(i) < ol2){
                        newLeafEdgesvaluesT2[S2.get(i)] = T2.getLeafEdgeAttribs()[OE2.getOrgLeaves2compLeaves(S2.get(i))].get(0) + tau*dDirection2[S2.get(i)];
                        if (B2.get(OE2.getBackMap(S2.get(i))) < ol2){
                            newLeafEdgesvaluesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newLeafEdgesvaluesT2[S2.get(i)];
                        } else {
                            newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i))) - ol2] -= newLeafEdgesvaluesT2[S2.get(i)];
                        }
                    } else {
                        newEdgesValuesT2[S2.get(i) - ol2] = EdgesT2.get(S2.get(i)-ol2).getNorm() + tau*dDirection2[S2.get(i)];
                        if (B2.get(OE2.getBackMap(S2.get(i))) < ol2){
                            newLeafEdgesvaluesT2[B2.get(OE2.getBackMap(S2.get(i)))] -= newEdgesValuesT2[S2.get(i) - ol2];
                        } else {
                            newEdgesValuesT2[B2.get(OE2.getBackMap(S2.get(i))) - ol2] -= newEdgesValuesT2[S2.get(i) - ol2];
                        }
                    }
                }
                
            
                //Computing the new values of the interior edges of the trees 
                for (int i = 0; i < newEdgesT1.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT1[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT1.get(i).setAttribute(tempEA);
                }
            
                for (int i = 0; i < newEdgesT2.size(); i++){
                    double[] tempVecEA = {newEdgesValuesT2[i]};
                    EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                    newEdgesT2.get(i).setAttribute(tempEA);
                }
                
                
                //We also need to change the values in the Leaf Edge attribs in the unrestricted case. 
            
                EdgeAttribute[] newT1LeafEdgeAtt = T1.getCopyLeafEdgeAttribs();
                EdgeAttribute[] newT2LeafEdgeAtt = T2.getCopyLeafEdgeAttribs();
            
                if (!restricted){
                    for (int i = 0; i < ol1; i++){
                        double[] tempVecEA = {newLeafEdgesvaluesT1[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        newT1LeafEdgeAtt[OE1.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                    }
                    for (int i = 0; i < ol2; i++){
                        double[] tempVecEA = {newLeafEdgesvaluesT2[i]};
                        EdgeAttribute tempEA = new EdgeAttribute(tempVecEA);
                        newT2LeafEdgeAtt[OE2.getOrgLeaves2compLeaves(i)].setEdgeAttribute(tempEA);
                    }
                }
            
            
                T1 = new PhyloTree(newEdgesT1, T1.getLeaf2NumMap(), newT1LeafEdgeAtt, false);
                T2 = new PhyloTree(newEdgesT2, T2.getLeaf2NumMap(), newT2LeafEdgeAtt, false);
                
                tempGeode = getGeodesic(T1, T2, null);
                
            }
            
        }
        
        //Getting the final values after the gradient descent has been performed. 
        Tree1 = T1;
        Tree2 = T2;
        FinalGeode = tempGeode;
        Distance = FinalGeode.getDist();
        
    }// end of Constructor 2
    
    //Getters & Printers
    public PhyloTree getFirstTree(){
        return Tree1;
    }
    
    public PhyloTree getSecondTree(){
        return Tree2;
    }
    
    public double getDistance(){
        return Distance;
    }
    
    public Geodesic getFinalGeode(){
        return FinalGeode;
    }
    
    public void PrintSummary(){
        System.out.println("The distance between the orthant extension spaces is " + Distance);
                PhyloNicePrinter treePrinter = new PhyloNicePrinter();
                System.out.println("Best Tree 1: ");
                System.out.println(treePrinter.toString(Tree1));
                System.out.println("");
                System.out.println("Best Tree 2: ");
                System.out.println(treePrinter.toString(Tree2));
            
    }
}

