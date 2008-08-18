/*
 * Copyright (c) 2008, AIST, the University of Tokyo and General Robotix Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 * National Institute of Advanced Industrial Science and Technology (AIST)
 * General Robotix Inc. 
 */
#ifndef UTILITIES_H
#define UTILITIES_H

#include "CollisionData.h"
#include "Opcode/Opcode.h"
#include <boost/numeric/ublas/matrix.hpp>
#include <math.h>

typedef boost::numeric::ublas::bounded_matrix<double,3,3,boost::numeric::ublas::column_major> dmatrix33;

#define vecProd(mat,vec) boost::numeric::ublas::prod(mat, vec)
#define outerProd(ret, vec1, vec2) \
   ret[0] = vec1[1]*vec2[2] - vec1[2]*vec2[1]; \
   ret[1] = vec1[2]*vec2[0] - vec1[0]*vec2[2]; \
   ret[2] = vec1[0]*vec2[1] - vec1[1]*vec2[0] 
#define innerProd(vec1, vec2) boost::numeric::ublas::inner_prod(vec1, vec2)
#define vecNorm(vec) sqrt(boost::numeric::ublas::inner_prod(vec, vec))
#define vecNormalize(vec) \
  { \
    double Vnormalize_d = 1.0 / sqrt(boost::numeric::ublas::inner_prod(vec, vec)); \
    vec[0] *= Vnormalize_d; \
    vec[1] *= Vnormalize_d; \
    vec[2] *= Vnormalize_d; \
  }


class tri
{
public:
  int id;
  dvector3 p1, p2, p3;
};

class col_tri
{
public:
  int status; // 0: unvisited, 1: visited, 2: included in the convex neighbor 
  dvector3 p1, p2, p3;
  dvector3 n;
};

const int CD_OK = 0;
const int CD_ALL_CONTACTS = 1;
const int CD_FIRST_CONTACT = 2;
const int CD_ERR_COLLIDE_OUT_OF_MEMORY = 2;

enum {
  FV = 1,
  VF,
  EE
};

int tri_tri_overlap(dvector3 &P1,
		    dvector3 &P2,
		    dvector3 &P3,
		    dvector3 &Q1,
		    dvector3 &Q2,
		    dvector3 &Q3,
		    collision_data *col_p) ;

int
insert_collision_pair(const Opcode::AABBCollisionNode *b1,
		      const Opcode::AABBCollisionNode *b2,
		      int id1, int id2,
		      int num_of_i_points,
		      dvector3 i_points[4],
		      dvector3 &n_vector,
		      double depth,
		      dvector3 &n1,
		      dvector3 &m1,
		      int ctype,
		      Opcode::MeshInterface *mesh1,
		      Opcode::MeshInterface *mesh2);

#endif
