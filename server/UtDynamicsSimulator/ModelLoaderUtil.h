// -*- mode: c++; indent-tabs-mode: t; tab-width: 4; c-basic-offset: 4; -*-
/*
 * Copyright (c) 2008, AIST, the University of Tokyo and General Robotix Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 * The University of Tokyo
 * National Institute of Advanced Industrial Science and Technology (AIST)
 * General Robotix Inc. 
 */
#ifndef MODEL_LOADER_UTIL_H_INCLUDED
#define MODEL_LOADER_UTIL_H_INCLUDED

#include <string>
#include <sstream>

#include <hrpCorba/ModelLoader.hh>
#include "hrpCorba/ORBwrap.h"
#include "World.h"
#include "hrpModelExportDef.h"

class World;

namespace OpenHRP
{
	HRPMODEL_EXPORT int loadBodyFromBodyInfo(World* world, const char* name, BodyInfo_ptr bodyInfo);
	HRPMODEL_EXPORT int loadBodyFromModelLoader(World* world, const char* name,const char *url, CORBA_ORB_var orb);
	HRPMODEL_EXPORT int loadBodyFromModelLoader(World* world, const char* name, const char *url, CosNaming::NamingContext_var cxt);
	HRPMODEL_EXPORT int loadBodyFromModelLoader(World* world, const char* name, const char *url, int argc, char *argv[]);
	HRPMODEL_EXPORT int loadBodyFromModelLoader(World* world, const char* name, const char *url, std::istringstream& strm);
};


#endif
