/*  Lattice Boltzmann sample, written in C++, using the OpenLB
 *  library
 *
 *  Copyright (C) 2011-2013 Mathias J. Krause, Thomas Henn, Tim Dornieden
 *  E-mail contact: info@openlb.net
 *  The most recent release of OpenLB can be downloaded at
 *  <http://www.openlb.net/>
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this program; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA  02110-1301, USA.
 */

/* cylinder3d.cpp:
 * This example examines a steady flow past a cylinder placed in a channel.
 * The cylinder is offset somewhat from the center of the flow to make the
 * steady-state symmetrical flow unstable. At the inlet, a Poiseuille profile is
 * imposed on the velocity, whereas the outlet implements a Dirichlet pressure
 * condition set by p = 0.
 * Inspired by "Benchmark Computations of Laminar Flow Around
 * a Cylinder" by M.Schäfer and S.Turek. For high resolution, low
 * latticeU, and enough time to converge, the results for pressure drop, drag
 * and lift lie within the estimated intervals for the exact results.
 * An unsteady flow with Karman vortex street can be created by changing the
 * Reynolds number to Re=100.
 * It also shows the usage of the STL-reader and explains how
 * to set boundary conditions automatically.
 */


#include "olb3D.h"
#include "olb3D.hh"   // include full template code
#include <vector>
#include <cmath>
#include <iostream>
#include <fstream>

using namespace olb;
using namespace olb::descriptors;
using namespace olb::graphics;
using namespace olb::util;
using namespace std;

//typedef double T;
//#define DESCRIPTOR ShanChenDynOmegaForcedD3Q19Descriptor

typedef double T;
#define DESCRIPTOR ShanChenDynOmegaForcedD3Q19Descriptor


// Parameters for the simulation setup
const int N = 1;        // resolution of the model
const int M = 1;        // time discretization refinement
const T Re = 10000.;       // Reynolds number
const T maxPhysT = 200.; // max. simulation time in s, SI unit


/// Stores data from stl file in geometry in form of material numbers
SuperGeometry3D<T> prepareGeometry( LBconverter<T> const& converter)
{

  OstreamManager clout( std::cout,"prepareGeometry" );
  clout << "Prepare Geometry ..." << std::endl;

  /// Definition of the geometry of the closure
  Vector<T,3> C0(60,60,0);
  Vector<T,3> C1(60,60,40);
  Vector<T,3> C2(60,60,4);
  Vector<T,3> C3(60,60,36);
  Vector<T,3> C4(60,60,56);
  Vector<T,3> C5(60,60,60);
  Vector<T,3> C6(60,60,44);

  T inner_radius1 = 24 ;  // inner radius of the widest part
  T radius1 = 30 ;  // radius of the widest part
  T inner_radius2 = 14 ;  // inner radius of the widest part
  T radius2 = 20 ;  // radius of the widest part

  IndicatorCylinder3D<T> cyl1(C0, C1, radius2);
  IndicatorCylinder3D<T> inner_cyl1(C0, C6, inner_radius2);
  IndicatorCylinder3D<T> cyl2(C1, C5, radius1);
  IndicatorCylinder3D<T> inner_cyl2(C1, C4, inner_radius1);

  IndicatorCylinder3D<T> outflow(C0, C2, inner_radius2);
  IndicatorCylinder3D<T> inflow_large(C1, C6, inner_radius1);
  IndicatorCylinder3D<T> inflow_small(C1, C6, radius2);

  IndicatorCylinder3D<T> ring_small(C1, C6, inner_radius2);
  IndicatorCylinder3D<T> ring_large(C1, C6, radius2);

  IndicatorIdentity3D<T> closure(cyl1 + cyl2);
  IndicatorIdentity3D<T> inner_closure(inner_cyl1 + inner_cyl2);
  IndicatorIdentity3D<T> inflow(inflow_large - inflow_small);
  IndicatorIdentity3D<T> ring(ring_large - ring_small);

  /// Build CoboidGeometry from IndicatorF (weights are set, remove and shrink is done)
  //CuboidGeometry3D<T>* cuboidGeometry = new CuboidGeometry3D<T>(closure, 1./N, 20*singleton::mpi().getSize() );
  CuboidGeometry3D<T>* cuboidGeometry = new CuboidGeometry3D<T>(closure, 1./N, 20);

  /// Build LoadBalancer from CuboidGeometry (weights are respected)
  HeuristicLoadBalancer<T>* loadBalancer = new HeuristicLoadBalancer<T>(*cuboidGeometry);

  /// Default instantiation of superGeometry
  SuperGeometry3D<T> superGeometry(*cuboidGeometry, *loadBalancer, 2);

  /// Set the material number for fluid
  superGeometry.rename(0,2,closure);
  superGeometry.checkForErrors();
  superGeometry.rename(2,1,inner_closure);
  superGeometry.checkForErrors();
  superGeometry.rename(1,2,ring);
  superGeometry.checkForErrors();
  superGeometry.clean();
  superGeometry.checkForErrors();

  /// Set boundary voxels by rename material numbers
  superGeometry.rename(1, 3, inflow);
  superGeometry.innerClean();
  superGeometry.checkForErrors();
  superGeometry.rename(1, 4, outflow);
  superGeometry.innerClean();
  superGeometry.checkForErrors();

  /// Removes all not needed boundary voxels outside the surface
  superGeometry.clean();
  /// Removes all not needed boundary voxels inside the surface
  superGeometry.innerClean();
  superGeometry.checkForErrors();

  superGeometry.print();
  superGeometry.getStatistics().print();
  superGeometry.communicate();

  clout << "Prepare Geometry ... OK" << std::endl;
  return superGeometry;
}

/// Set up the geometry of the simulation
void prepareLattice( SuperLattice3D<T,DESCRIPTOR>& sLattice,
                     LBconverter<T> const& converter,
                     Dynamics<T, DESCRIPTOR>& bulkDynamics,
                     sOnLatticeBoundaryCondition3D<T,DESCRIPTOR>& bc,
                     sOffLatticeBoundaryCondition3D<T,DESCRIPTOR>& offBc,
                     SuperGeometry3D<T>& superGeometry )
{

  OstreamManager clout( std::cout,"prepareLattice" );
  clout << "Prepare Lattice ..." << std::endl;

  const T omega = converter.getOmega();

  /// Material=0 -->do nothing
  sLattice.defineDynamics( superGeometry, 0, &instances::getNoDynamics<T, DESCRIPTOR>() );

  /// Material=1 -->bulk dynamics
  sLattice.defineDynamics( superGeometry, 1, &bulkDynamics );

  /// Material=2 -->bounce back
  sLattice.defineDynamics( superGeometry, 2, &instances::getBounceBack<T, DESCRIPTOR>() );

  /// Material=3 -->bulk dynamics (inflow)
  sLattice.defineDynamics( superGeometry, 3, &bulkDynamics );

  /// Material=4 -->bulk dynamics (outflow)
  sLattice.defineDynamics( superGeometry, 4, &bulkDynamics );

  /// Setting of the boundary conditions
  bc.addVelocityBoundary( superGeometry, 3, omega );
  bc.addPressureBoundary( superGeometry, 4, omega );

  /// Initial conditions
  T Ly = converter.numCells(2.);
  std::vector<T> poiseuilleForce(3,T());
  poiseuilleForce[2] = -80.*converter.getLatticeNu()*converter.getLatticeU() / (Ly*Ly);
  AnalyticalConst3D<T,T> force(poiseuilleForce);

  // Initialize force
  sLattice.defineExternalField(superGeometry, 1,
                               DESCRIPTOR<T>::ExternalField::forceBeginsAt,
                               DESCRIPTOR<T>::ExternalField::sizeOfForce, force );
  sLattice.defineExternalField(superGeometry, 2,
                               DESCRIPTOR<T>::ExternalField::forceBeginsAt,
                               DESCRIPTOR<T>::ExternalField::sizeOfForce, force );

  /// Initial conditions
  AnalyticalConst3D<T,T> rhoF( 1 );
  std::vector<T> velocity( 3,T() );
  AnalyticalConst3D<T,T> uF( velocity );

  // Initialize all values of distribution functions to their local equilibrium
  sLattice.defineRhoU( superGeometry, 1, rhoF, uF );
  sLattice.iniEquilibrium( superGeometry, 1, rhoF, uF );
  sLattice.defineRhoU( superGeometry, 3, rhoF, uF );
  sLattice.iniEquilibrium( superGeometry, 3, rhoF, uF );
  sLattice.defineRhoU( superGeometry, 4, rhoF, uF );
  sLattice.iniEquilibrium( superGeometry, 4, rhoF, uF );

  /// Make the lattice ready for simulation
  sLattice.initialize();

  clout << "Prepare Lattice ... OK" << std::endl;
}

/// Generates a slowly increasing inflow for the first iTMaxStart timesteps
void setBoundaryValues( SuperLattice3D<T, DESCRIPTOR>& sLattice,
                        LBconverter<T> const& converter, int iT, T maxPhysT,
                        SuperGeometry3D<T>& superGeometry )
{

  OstreamManager clout(std::cout,"setBoundaryValues");

  // No of time steps for smooth start-up
  int iTmaxStart = converter.numTimeSteps(maxPhysT*0.8);
  int iTperiod = 50;

  if (iT==0) {
	/// Make the lattice ready for simulation
	sLattice.initialize();
  }

  else if (iT%iTperiod==0 && iT<= iTmaxStart) {
	//clout << "Set Boundary Values ..." << std::endl;

	//SinusStartScale<T,int> startScale(iTmaxStart, (T) 1);
	PolynomialStartScale<T,int> startScale(iTmaxStart, T(1));
	int iTvec[1]={iT};
	T frac = T();
	startScale(&frac,iTvec);

	// Creates and sets the Poiseuille inflow profile using functors
	std::vector<T> maxVelocity(3,0);
	maxVelocity[0] = /*2.25*/frac*converter.getLatticeU();

	RectanglePoiseuille3D<T> poiseuilleU(superGeometry, 3, maxVelocity, converter.getLatticeL(), converter.getLatticeL(), converter.getLatticeL());
	sLattice.defineU(superGeometry, 3, poiseuilleU);

	//clout << "step=" << iT << "; scalingFactor=" << frac << std::endl;
  }
  //clout << "Set Boundary Values ... ok" << std::endl;
}

/// Computes the pressure drop between the voxels before and after the cylinder
void getResults( SuperLattice3D<T, DESCRIPTOR>& sLattice,
                 LBconverter<T>& converter, int iT,
                 SuperGeometry3D<T>& superGeometry, Timer<T>& timer )
{

  OstreamManager clout(std::cout,"getResults");
  SuperVTKwriter3D<T> vtkWriter("closure3d");

  if (iT==0) {
	/// Writes the converter log file
	writeLogFile(converter, "closure3d");
	/// Writes the geometry, cuboid no. and rank no. as vti file for visualization
	SuperLatticeGeometry3D<T, DESCRIPTOR> geometry(sLattice, superGeometry);
	SuperLatticeCuboid3D<T, DESCRIPTOR> cuboid(sLattice);
	SuperLatticeRank3D<T, DESCRIPTOR> rank(sLattice);
	vtkWriter.write(geometry);
	vtkWriter.write(cuboid);
	vtkWriter.write(rank);
	vtkWriter.createMasterFile();
  }

  /// Writes the vtk files
  if (iT%converter.numTimeSteps(1.)==0) {
	// Create the data-reading functors...
	SuperLatticePhysVelocity3D<T, DESCRIPTOR> velocity(sLattice, converter);
	SuperLatticePhysPressure3D<T, DESCRIPTOR> pressure(sLattice, converter);
	vtkWriter.addFunctor( velocity );
	vtkWriter.addFunctor( pressure );
	vtkWriter.write(iT);

	SuperEuklidNorm3D<T, DESCRIPTOR> normVel( velocity );
	BlockLatticeReduction3D<T, DESCRIPTOR> planeReduction( normVel, 0, 0, -1 );
	BlockGifWriter<T> gifWriter;
	gifWriter.write( planeReduction, iT, "vel" ); // scaled
  }

  /// Writes output on the console
  if (iT%converter.numTimeSteps(1.)==0) {
	timer.update(iT);
	timer.printStep();
	sLattice.getStatistics().print(iT, converter.physTime(iT));

  }
}

int main( int argc, char* argv[] )
{

  /// === 1st Step: Initialization ===
  olbInit( &argc, &argv );
  singleton::directories().setOutputDir( "./tmp/" );
  OstreamManager clout( std::cout,"main" );
  // display messages from every single mpi process
  //clout.setMultiOutput(true);

  LBconverter<T> converter(
    ( int ) 3,                             // dim
    (T)   1./N,                            // latticeL_
    (T)   0.02/M,                          // latticeU_
    (T)   0.1,                             // charNu_
    (T)   0.1,                             // charL_ = 1
    (T)   2.                               // charU_ = 1
  );
  converter.print();

  /// === 2nd Step: Prepare Geometry ===

  SuperGeometry3D<T> superGeometry(prepareGeometry(converter) );

  /// === 3rd Step: Prepare Lattice ===
  SuperLattice3D<T, DESCRIPTOR> sLattice(superGeometry);

  RLBdynamics<T, DESCRIPTOR> bulkDynamics(converter.getOmega(), instances::getBulkMomenta<T, DESCRIPTOR>());

  sOnLatticeBoundaryCondition3D<T, DESCRIPTOR> sBoundaryCondition(sLattice);
  createInterpBoundaryCondition3D<T, DESCRIPTOR> (sBoundaryCondition);

  sOffLatticeBoundaryCondition3D<T, DESCRIPTOR> sOffBoundaryCondition(sLattice);
  createBouzidiBoundaryCondition3D<T, DESCRIPTOR> (sOffBoundaryCondition);

  prepareLattice(sLattice, converter, bulkDynamics, sBoundaryCondition, sOffBoundaryCondition, superGeometry);

  Timer<T> timer(converter.numTimeSteps(maxPhysT), superGeometry.getStatistics().getNvoxel() );
  timer.start();
  getResults(sLattice, converter, 0, superGeometry, timer);

  for ( int iT = 0; iT < converter.numTimeSteps( maxPhysT ); ++iT ) {

    /// === 5th Step: Definition of Initial and Boundary Conditions ===
    setBoundaryValues( sLattice, converter, iT, maxPhysT, superGeometry );

    /// === 6th Step: Collide and Stream Execution ===
    sLattice.collideAndStream();

    /// === 7th Step: Computation and Output of the Results ===
    getResults( sLattice, converter, iT, superGeometry, timer);
  }

  timer.stop();
  timer.printSummary();
}

