/*
 *    Segment.java
 *    Copyright (C) 2012 Universitat Politècnica de Catalunya
 *    @author Massimo Quadrana <max.square@gmail.com>
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package moa.core;

import CharmBitsetOrig.AlgoCharm_Bitset;
import CharmBitsetOrig.Context;
import CharmBitsetOrig.Itemset;
import CharmBitsetOrig.Itemsets;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.SparseInstance;
import moa.utils.Configuration;


/*
    (Tomas Chovanak) Change in toItemset method.
*/
public class Segment implements Serializable{

    private static final long serialVersionUID = -5259948122890387234L;
    private Context context;
    private int MAX_ITEMSET_LENGTH;
    private double minSupport;
    
    /**
     * Default constructor. Creates a new empty segment.
     */
    public Segment(double minSupport, int maxItemsetLength) {        
        this.context = new Context();
        this.minSupport = minSupport;
        this.MAX_ITEMSET_LENGTH = maxItemsetLength;
    }

    /**
     * Adds a new itemset to the segment
     * @param itemset itemset to be added
     */
    public void addItemset(Instance instance) {
        context.addItemset(toItemset(instance));
    }
    
    /**
     * Removes all itemsets stored in the segment.
     */
    public void clear() {
        context = new Context();
    }

    /**
     * Returns the length of the segment. (Useful in case variable lenght segments)
     * @return lenght of the segment
     */
    public int size() {
        return context.size();
    }
    
    /**
     * Return the list of FCIs mined in the current segment in size ascending order
     * @return list of FCIs
     */
    public List<SemiFCI> getFCI() {
        AlgoCharm_Bitset charm = new AlgoCharm_Bitset();
        Itemsets closedItemsets = charm.runAlgorithm(context, minSupport, 1000000);
        System.out.println("Compute FCIs:" + charm.getExecTime() + "ms\n (CHARM-BITSET)");
        System.out.println(closedItemsets.getItemsetsCount() + " FCIs found in the last segment (CHARM-BITSET)");
        List<SemiFCI> fciSet = new ArrayList<SemiFCI>();
        double startUpdateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        for(int levelIndex = 0; levelIndex < closedItemsets.getLevels().size(); levelIndex++){
            double endUpdateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
            if (this.MAX_ITEMSET_LENGTH != -1 && levelIndex > this.MAX_ITEMSET_LENGTH){
                break;
            }
            if(((endUpdateTime - startUpdateTime)/1e6 > Configuration.MAX_UPDATE_TIME)){
                break;
            }
            List<Itemset> level = closedItemsets.getLevels().get(levelIndex);
            for(Itemset itemset: level){
                SemiFCI newFci = new SemiFCI(new ArrayList<Integer>(itemset.getItems()),itemset.getAbsoluteSupport()); 
                fciSet.add(newFci);
                
            }
            
        }
        return fciSet;
    }
    
    /**
     * Get sparse itemset representation of the current binary instance
     * (Tomas Chovanak) Because of used format of session file and instance being made from row of this file, 
     * where each row represents session. And first number is groupid , second uid. 
     * I changed iterator to iterate from 2nd item in instance.
     * @param inst current transaction instance
     * @return an itemset composed by the indices of the non-zero elements in the instance
     */    
    private Itemset toItemset(Instance inst){
        Itemset itemset = new Itemset();
        for(int val = 2; val < inst.numValues(); val++){ // from val 2 because first val is groupid and second uid
            itemset.addItem((int)inst.value(val));
        }
        return itemset;
    }
}
